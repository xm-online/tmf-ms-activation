package com.icthh.xm.tmf.ms.activation.service;

import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_AUTH_CONTEXT;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_TENANT_CONTEXT;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.INVALID_SPECIFICATION;
import static java.lang.Boolean.parseBoolean;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.UUID.randomUUID;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.config.client.repository.TenantListRepository;
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
import com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.service.SagaSpecService.InvalidSagaSpecificationException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.test.binder.MessageCollectorAutoConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit4.SpringRunner;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ActivationApp.class, SecurityBeanOverrideConfiguration.class})
@EnableAutoConfiguration(exclude = MessageCollectorAutoConfiguration.class)
public class SagaServiceImplTest {

    @Autowired
    private SagaService sagaService;

    @MockBean
    private SagaTaskExecutor sagaTaskExecutor;

    @Autowired
    private LepManager lepManager;

    @Autowired
    private TenantContextHolder tenantContextHolder;

    @Mock
    private XmAuthenticationContextHolder authContextHolder;

    @Mock
    private XmAuthenticationContext context;

    @MockBean
    private RetryService retryService;

    @MockBean
    private EventsSender eventsSender;

    @MockBean
    private SagaTransactionRepository transactionRepository;

    @MockBean
    private SagaEventRepository sagaEventRepository;

    @Before
    public void setup() {

        TenantContextUtils.setTenant(tenantContextHolder, "TEST_TENANT");
        MockitoAnnotations.initMocks(this);
        when(authContextHolder.getContext()).thenReturn(context);
        when(context.getUserKey()).thenReturn(Optional.of("userKey"));

        lepManager.beginThreadContext(ctx -> {
            ctx.setValue(THREAD_CONTEXT_KEY_TENANT_CONTEXT, tenantContextHolder.getContext());
            ctx.setValue(THREAD_CONTEXT_KEY_AUTH_CONTEXT, authContextHolder.getContext());
        });

        //String config = loadFile("spec/activation-spec-group-test.yml");
        //sagaSpecService.onRefresh("/config/tenants/XM/activation/activation-spec.yml", config);
    }

    @SneakyThrows
    public static String loadFile(String path) {
        return IOUtils.toString(new ClassPathResource(path).getInputStream(), UTF_8);
    }

    @After
    public void tearDown() {
        lepManager.endThreadContext();
        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
    }

    @Test
    public void testTransactionNotFound() {
        String txId = randomUUID().toString();
        when(transactionRepository.findById(txId)).thenReturn(Optional.empty());
        SagaEvent sagaEvent = new SagaEvent()
                .setTenantKey("TEST_TENANT")
                .setTransactionId(txId)
                .setTypeKey("TEST_NOT_FOUND_TYPE_KEY");
        sagaService.onSagaEvent(sagaEvent);
        verify(eventsSender).resendEvent(sagaEvent);
        verifyZeroInteractions(sagaTaskExecutor);
        verifyZeroInteractions(retryService);
        verifyZeroInteractions(sagaEventRepository);
    }

    @Test
    public void testTransactionSpecNotFound() {
        String txId = randomUUID().toString();
        SagaTransaction sagaTransaction = new SagaTransaction()
                .setSagaTransactionState(SagaTransactionState.NEW)
                .setTypeKey("TEST_NOT_FOUND_TRANSACTION");

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(sagaTransaction));
        SagaEvent sagaEvent = new SagaEvent()
                .setTenantKey("TEST_TENANT")
                .setTransactionId(txId)
                .setTypeKey("TEST_NOT_FOUND_EVENT");

        SagaEvent savedSagaEvent = new SagaEvent()
                .setTenantKey("TEST_TENANT")
                .setStatus(INVALID_SPECIFICATION)
                .setTransactionId(txId)
                .setTypeKey("TEST_NOT_FOUND_EVENT");

        sagaService.onSagaEvent(sagaEvent);
        verify(sagaEventRepository).save(refEq(savedSagaEvent, "id"));
        verifyNoMoreInteractions(sagaEventRepository);
        verifyZeroInteractions(eventsSender);
        verifyZeroInteractions(sagaTaskExecutor);
        verifyZeroInteractions(retryService);
    }

    @Test(expected = InvalidSagaSpecificationException.class)
    public void testContinuationTransactionSpecNotFound() {
        String txId = randomUUID().toString();

        SagaEvent sagaEvent = new SagaEvent()
                .setTenantKey("TEST_TENANT")
                .setTransactionId(txId)
                .setTypeKey("TEST_NOT_FOUND_EVENT");
        when(sagaEventRepository.findById(sagaEvent.getId())).thenReturn(Optional.of(sagaEvent));

        sagaService.continueTask(sagaEvent.getId(), Map.of());
    }

}
