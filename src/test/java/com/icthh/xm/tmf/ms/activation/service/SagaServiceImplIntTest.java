package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.commons.lep.api.LepManagementService;
import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.tmf.ms.activation.AbstractSpringBootTest;
import com.icthh.xm.tmf.ms.activation.config.ApplicationStartup;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.service.SagaSpecService.InvalidSagaSpecificationException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;
import java.util.Optional;

import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.INVALID_SPECIFICATION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Slf4j
public class SagaServiceImplIntTest extends AbstractSpringBootTest {

    @Autowired
    private SagaService sagaService;

    @MockitoBean
    private SagaTaskExecutor sagaTaskExecutor;

    @Autowired
    private LepManagementService lepManager;

    @Autowired
    private TenantContextHolder tenantContextHolder;

    @Mock
    private XmAuthenticationContextHolder authContextHolder;

    @Mock
    private XmAuthenticationContext context;

    @MockitoBean
    private ApplicationStartup applicationStartup;

    @MockitoBean
    private RetryService retryService;

    @MockitoBean
    private EventsSender eventsSender;

    @MockitoBean
    private SagaTransactionRepository transactionRepository;

    @MockitoBean
    private SagaEventRepository sagaEventRepository;

    @BeforeEach
    public void setup() {

        TenantContextUtils.setTenant(tenantContextHolder, "TEST_TENANT");
        MockitoAnnotations.openMocks(this);
        when(authContextHolder.getContext()).thenReturn(context);
        when(context.getUserKey()).thenReturn(Optional.of("userKey"));

        lepManager.beginThreadContext();
    }

    @SneakyThrows
    public static String loadFile(String path) {
        return IOUtils.toString(new ClassPathResource(path).getInputStream(), UTF_8);
    }

    @AfterEach
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
        verifyNoInteractions(sagaTaskExecutor);
        verifyNoInteractions(retryService);
        verifyNoInteractions(sagaEventRepository);
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
        verifyNoInteractions(eventsSender);
        verifyNoInteractions(sagaTaskExecutor);
        verifyNoInteractions(retryService);
    }

    @Test
    public void testContinuationTransactionSpecNotFound() {
        String txId = randomUUID().toString();

        SagaEvent sagaEvent = new SagaEvent()
                .setTenantKey("TEST_TENANT")
                .setTransactionId(txId)
                .setTypeKey("TEST_NOT_FOUND_EVENT");
        when(sagaEventRepository.findById(sagaEvent.getId())).thenReturn(Optional.of(sagaEvent));

        assertThrows(InvalidSagaSpecificationException.class, () -> sagaService.continueTask(sagaEvent.getId(), Map.of()));
    }

}
