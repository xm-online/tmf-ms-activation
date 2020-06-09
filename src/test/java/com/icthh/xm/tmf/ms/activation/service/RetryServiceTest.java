package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.commons.lep.XmLepScriptConfigServerResourceLoader;
import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.lep.api.LepManager;
import com.icthh.xm.tmf.ms.activation.ActivationApp;
import com.icthh.xm.tmf.ms.activation.config.SecurityBeanOverrideConfiguration;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binding.BinderAwareChannelResolver;
import org.springframework.cloud.stream.test.binder.MessageCollectorAutoConfiguration;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.icthh.xm.commons.i18n.I18nConstants.LANGUAGE;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_AUTH_CONTEXT;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_TENANT_CONTEXT;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.ON_RETRY;
import static com.icthh.xm.tmf.ms.activation.service.SagaServiceTest.loadFile;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ActivationApp.class, SecurityBeanOverrideConfiguration.class})
@EnableAutoConfiguration(exclude = MessageCollectorAutoConfiguration.class)
public class RetryServiceTest {


    @Autowired
    private XmLepScriptConfigServerResourceLoader lepResourceLoader;
    @Autowired
    private ThreadPoolTaskScheduler threadPoolTaskScheduler;
    @Autowired
    private LepManager lepManager;
    @Autowired
    private TenantContextHolder tenantContextHolder;

    private RetryService retryService;
    @Mock
    private TenantUtils tenantUtils;
    @Mock
    private SagaEventRepository eventRepository;
    @Mock
    private EventsSender eventsSender;
    @Mock
    private XmAuthenticationContext context;
    @Mock
    private XmAuthenticationContextHolder authContextHolder;
    @MockBean
    private BinderAwareChannelResolver binderAwareChannelResolver;
    @Mock
    private MessageChannel messageChannel;

    @After
    public void destroy() {
        lepManager.endThreadContext();
        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
    }

    private static final String TENANT = "XM";
    private static final String TYPE_KEY = "TEST-TYPE-KEY";
    private static final String FIRST_TASK_KEY = "TASK-1";
    private static final String COUNT_DOWN_LOCK_KEY = "COUNT_DOWN_LOCK";

    private SagaSpecService specService;


    @Before
    public void before() throws IOException {

        when(messageChannel.send(any())).thenReturn(false)
            .thenReturn(false)
            .thenReturn(false)
            .thenReturn(true);

        when(binderAwareChannelResolver.resolveDestination(any())).thenReturn(messageChannel);

        when(context.hasAuthentication()).thenReturn(true);
        when(context.getLogin()).thenReturn(Optional.of("testLogin"));
        when(context.getUserKey()).thenReturn(Optional.of("testUserKey"));
        when(context.getDetailsValue(LANGUAGE)).thenReturn(Optional.of("en"));

        when(authContextHolder.getContext()).thenReturn(context);

        lepManager.beginThreadContext(ctx -> {
            ctx.setValue(THREAD_CONTEXT_KEY_TENANT_CONTEXT, tenantContextHolder.getContext());
            ctx.setValue(THREAD_CONTEXT_KEY_AUTH_CONTEXT, authContextHolder.getContext());
        });

        specService = new SagaSpecService(tenantUtils);
        retryService = new RetryService(threadPoolTaskScheduler, eventsSender, eventRepository, tenantUtils);
        specService.onRefresh("/config/tenants/XM/activation/activation-spec.yml", loadFile("spec/activation-spec-retry-service-test.yml"));

    }

    @SneakyThrows
    @Test
    public void testRetry() {
        lepResourceLoader.onRefresh("/config/tenants/XM/activation/lep/service/retry/retryLimitExceeded$$around.groovy",
            "  event = lepContext.inArgs.sagaEvent" +
                " event.taskContext.put('data','data'); " +
                " event.taskContext.COUNT_DOWN_LOCK.countDown(); ");


        when(tenantUtils.getTenantKey()).thenReturn(TENANT);
        String txId = UUID.randomUUID().toString();
        String id = UUID.randomUUID().toString();

        CountDownLatch countDownLatch = new CountDownLatch(3);
        Map<String, Object> taskContext = new HashMap<>();
        taskContext.put(COUNT_DOWN_LOCK_KEY, countDownLatch);

        SagaEvent sagaEvent = new SagaEvent().setTenantKey(TENANT)
            .setId(id)
            .setTypeKey(FIRST_TASK_KEY)
            .setTransactionId(txId)
            .setCreateDate(Instant.now())
            .setTaskContext(taskContext);


        when(eventRepository.save(refEq(sagaEvent, "backOff", "retryNumber", "status"))).thenReturn(sagaEvent);
        when(eventRepository.findById(eq(id))).thenReturn(Optional.of(sagaEvent));

        SagaTaskSpec task = specService.getTransactionSpec(TYPE_KEY).getTask(FIRST_TASK_KEY);


        Mockito.doAnswer(invocation -> {

            SagaEvent event = (SagaEvent) invocation.getArguments()[0];
            retryService.retry(event, task, ON_RETRY);
            countDownLatch.countDown();
            return null;
        }).when(eventsSender).sendEvent(refEq(sagaEvent, "backOff", "retryNumber", "status"));


        retryService.retry(sagaEvent, task, ON_RETRY);


        countDownLatch.await(15, TimeUnit.SECONDS);
        if (1 == 1) {
            log.error("dadadqa");

        }

    }

}
