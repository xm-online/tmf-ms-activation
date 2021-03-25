package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.commons.lep.XmLepScriptConfigServerResourceLoader;
import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.lep.api.LepManager;
import com.icthh.xm.tmf.ms.activation.ActivationApp;
import com.icthh.xm.tmf.ms.activation.config.SecurityBeanOverrideConfiguration;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.hamcrest.collection.IsMapContaining;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.test.binder.MessageCollectorAutoConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.icthh.xm.commons.i18n.I18nConstants.LANGUAGE;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_AUTH_CONTEXT;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_TENANT_CONTEXT;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.IN_QUEUE;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.ON_RETRY;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.FAILED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;


@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ActivationApp.class, SecurityBeanOverrideConfiguration.class})
@EnableAutoConfiguration(exclude = MessageCollectorAutoConfiguration.class)
public class RetryServiceTest {


    @Autowired
    private XmLepScriptConfigServerResourceLoader lepResourceLoader;
    @Autowired
    private LepManager lepManager;
    @Autowired
    private TenantContextHolder tenantContextHolder;
    @MockBean
    private SagaEventRepository eventRepository;
    @MockBean
    private EventsSender eventsSender;
    @MockBean
    private SagaService sagaService;
    @Autowired
    private SagaSpecService sagaSpecService;
    @Mock
    private XmAuthenticationContext context;
    @Mock
    private XmAuthenticationContextHolder authContextHolder;

    @Autowired
    private RetryService retryService;

    private static final String TENANT = "XM";
    private static final String TYPE_KEY = "TEST-TYPE-KEY";
    private static final String FIRST_TASK_KEY = "TASK-1";


    @After
    public void destroy() {
        lepManager.endThreadContext();
        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
    }


    @Before
    public void init() {
        TenantContextUtils.setTenant(tenantContextHolder, TENANT);

        when(context.hasAuthentication()).thenReturn(true);
        when(context.getLogin()).thenReturn(Optional.of("testLogin"));
        when(context.getUserKey()).thenReturn(Optional.of("testUserKey"));
        when(context.getDetailsValue(LANGUAGE)).thenReturn(Optional.of("en"));

        when(authContextHolder.getContext()).thenReturn(context);

        lepManager.beginThreadContext(ctx -> {
            ctx.setValue(THREAD_CONTEXT_KEY_TENANT_CONTEXT, tenantContextHolder.getContext());
            ctx.setValue(THREAD_CONTEXT_KEY_AUTH_CONTEXT, authContextHolder.getContext());
        });

        sagaSpecService.onRefresh("/config/tenants/XM/activation/activation-spec.yml", loadFile("spec/activation-spec-retry-service-test.yml"));

    }

    @Test
    @SneakyThrows
    public void testRetry() {
        lepResourceLoader.onRefresh("/config/tenants/XM/activation/lep/service/retry/RetryLimitExceeded$$around.groovy", loadFile("/lep/RetryLimitExceeded$$around.groovy"));

        final String txId = UUID.randomUUID().toString();
        final String id = UUID.randomUUID().toString();

        CountDownLatch countDownLatch = new CountDownLatch(3);

        SagaEvent sagaEvent = new SagaEvent().setTenantKey(TENANT)
            .setId(id)
            .setTypeKey(FIRST_TASK_KEY)
            .setTransactionId(txId)
            .setCreateDate(Instant.now())
            .setTaskContext(new HashMap<>());


        final String[] excludedFields = new String[]{"backOff", "taskContext", "createDate", "retryNumber", "status"};

        when(eventRepository.save(refEq(sagaEvent, excludedFields))).thenReturn(sagaEvent);
        when(eventRepository.findById(eq(id))).thenReturn(Optional.of(sagaEvent));

        SagaTaskSpec task = sagaSpecService.getTransactionSpec(TYPE_KEY).getTask(FIRST_TASK_KEY);


        Mockito.doAnswer(invocation -> {
            SagaEvent event = (SagaEvent) invocation.getArguments()[0];
            retryService.retry(event, mockTx(), task, ON_RETRY);
            countDownLatch.countDown();
            return event;
        }).when(eventsSender).sendEvent(refEq(sagaEvent, excludedFields));

        retryService.retry(sagaEvent, mockTx(), task, ON_RETRY);
        countDownLatch.await(5, TimeUnit.SECONDS);

        verify(eventRepository, atLeastOnce()).findByStatus(any());

        verify(eventRepository, times(3)).findById(eq(id));

        verify(eventRepository, times(6)).save(
            refEq(inQueueSagaEvent(txId, id), "backOff", "taskContext", "retryNumber", "createDate"));


        verify(eventsSender, times(3)).sendEvent(
            refEq(inQueueSagaEvent(txId, id), "backOff", "taskContext", "createDate", "retryNumber"));

        Assert.assertThat(sagaEvent.getTaskContext(), IsMapContaining.hasEntry("test", "data"));

        verify(sagaService).changeTransactionState(txId, FAILED);
        verifyNoMoreInteractions(eventsSender);
        verifyNoMoreInteractions(eventRepository);
    }

    private SagaEvent inQueueSagaEvent(String txId, String id) {
        return new SagaEvent().setTenantKey(TENANT)
            .setId(id)
            .setStatus(IN_QUEUE)
            .setTypeKey(FIRST_TASK_KEY)
            .setTransactionId(txId);
    }

    @SneakyThrows
    public static String loadFile(String path) {
        InputStream cfgInputStream = new ClassPathResource(path).getInputStream();
        return IOUtils.toString(cfgInputStream, UTF_8);
    }

    private SagaTransaction mockTx() {
        return new SagaTransaction()
            .setKey("KEY")
            .setTypeKey(TYPE_KEY)
            .setSagaTransactionState(NEW);
    }

    @Test
    @SneakyThrows
    public void testRetryWithTaskResolver() {
        lepResourceLoader.onRefresh("/config/tenants/XM/activation/lep/service/retry/RetryLimitExceeded$$TEST_TYPE_KEY$$TASK_1$$around.groovy", loadFile("/lep/RetryLimitExceeded$$around.groovy"));

        final String txId = UUID.randomUUID().toString();
        final String id = UUID.randomUUID().toString();

        CountDownLatch countDownLatch = new CountDownLatch(3);

        SagaEvent sagaEvent = new SagaEvent().setTenantKey(TENANT)
            .setId(id)
            .setTypeKey(FIRST_TASK_KEY)
            .setTransactionId(txId)
            .setCreateDate(Instant.now())
            .setTaskContext(new HashMap<>());


        final String[] excludedFields = new String[]{"backOff", "taskContext", "createDate", "retryNumber", "status"};

        when(eventRepository.save(refEq(sagaEvent, excludedFields))).thenReturn(sagaEvent);
        when(eventRepository.findById(eq(id))).thenReturn(Optional.of(sagaEvent));

        SagaTaskSpec task = sagaSpecService.getTransactionSpec(TYPE_KEY).getTask(FIRST_TASK_KEY);


        Mockito.doAnswer(invocation -> {
            SagaEvent event = (SagaEvent) invocation.getArguments()[0];
            retryService.retry(event, mockTx(), task, ON_RETRY);
            countDownLatch.countDown();
            return event;
        }).when(eventsSender).sendEvent(refEq(sagaEvent, excludedFields));

        retryService.retry(sagaEvent, mockTx(), task, ON_RETRY);
        countDownLatch.await(5, TimeUnit.SECONDS);

        Assert.assertThat(sagaEvent.getTaskContext(), IsMapContaining.hasEntry("test", "data"));
        verify(sagaService).changeTransactionState(txId, FAILED);
    }

    @Test
    @SneakyThrows
    public void testResendInQueueEvent() {

        final String txId = UUID.randomUUID().toString();
        final String id = UUID.randomUUID().toString();

        SagaEvent sagaEvent = new SagaEvent().setTenantKey(TENANT)
                .setId(id)
                .setTypeKey(FIRST_TASK_KEY)
                .setTransactionId(txId)
                .setStatus(IN_QUEUE)
                .setCreateDate(Instant.now())
                .setTaskContext(new HashMap<>());

        when(eventRepository.findById(eq(id))).thenReturn(Optional.of(sagaEvent));

        retryService.doResend(sagaEvent);

        verify(eventRepository).findById(eq(id));
        verifyNoMoreInteractions(eventsSender);

        final String[] excludedFields = new String[]{"backOff", "taskContext", "createDate", "retryNumber"};
        when(eventRepository.save(refEq(sagaEvent, excludedFields))).thenReturn(sagaEvent);

        retryService.doResendInQueueEvent(sagaEvent);

        verify(eventRepository, times(2)).findById(eq(id));
        verify(eventRepository).save(refEq(sagaEvent, excludedFields));
        verify(eventsSender).sendEvent(refEq(sagaEvent, excludedFields));

        verifyNoMoreInteractions(eventsSender);
        verifyNoMoreInteractions(eventRepository);
    }
}
