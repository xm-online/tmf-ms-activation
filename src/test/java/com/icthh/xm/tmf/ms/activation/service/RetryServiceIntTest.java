package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.commons.lep.XmLepScriptConfigServerResourceLoader;
import com.icthh.xm.commons.lep.api.LepManagementService;
import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.tmf.ms.activation.AbstractSpringBootTest;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState;
import com.icthh.xm.tmf.ms.activation.domain.SagaType;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.hamcrest.collection.IsMapContaining;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.icthh.xm.commons.i18n.I18nConstants.LANGUAGE;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.FAILED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.IN_QUEUE;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.ON_RETRY;
import static com.icthh.xm.tmf.ms.activation.domain.spec.MockSagaType.fromTypeKey;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


@Slf4j
public class RetryServiceIntTest extends AbstractSpringBootTest {


    @Autowired
    private XmLepScriptConfigServerResourceLoader lepResourceLoader;
    @Autowired
    private LepManagementService lepManager;
    @Autowired
    private TenantContextHolder tenantContextHolder;
    @MockBean
    private SagaEventRepository eventRepository;
    @MockBean
    private EventsSender eventsSender;
    @MockBean
    private SagaTransactionRepository transactionRepository;
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
    private static final SagaType TYPE = fromTypeKey("TEST-TYPE-KEY");
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

        lepManager.beginThreadContext();
        sagaSpecService.onRefresh("/config/tenants/XM/activation/activation-spec.yml", loadFile("spec/activation-spec-retry-service-test.yml"));
    }

    @Test
    @SneakyThrows
    public void testRetry() {
        lepResourceLoader.onRefresh("/config/tenants/XM/activation/lep/service/retry/RetryLimitExceeded$$around.groovy", loadFile("/lep/RetryLimitExceeded$$around.groovy"));

        final String txId = UUID.randomUUID().toString();
        final String id = UUID.randomUUID().toString();
        final SagaTransaction transaction = mockTx().setId(txId);
        final int maxRetryCount = 3;
        CountDownLatch countDownLatch = new CountDownLatch(maxRetryCount);

        SagaEvent sagaEvent = newSagaEvent(txId, id);

        //return new TX object each time for state transition verifying
        when(transactionRepository.findById(eq(txId))).thenReturn(
            //4 calls in RetryService.changeTransactionState
            Optional.of(newSagaTransaction(txId)), Optional.of(newSagaTransaction(txId)),
            Optional.of(newSagaTransaction(txId)), Optional.of(newSagaTransaction(txId)),
            //3 calls in RetryService.isTxStateChangeAllowed
            Optional.of(newSagaTransaction(txId)), Optional.of(newSagaTransaction(txId)),
            Optional.of(newSagaTransaction(txId)));

        final String[] excludedFields = new String[]{"backOff", "taskContext", "createDate", "retryNumber"};

        //return new event object each time for state transition verifying
        when(eventRepository.saveAndFlush(refEq(onRetrySagaEvent(txId, id), excludedFields)))
            .thenReturn(onRetrySagaEvent(txId, id), onRetrySagaEvent(txId, id), onRetrySagaEvent(txId, id));
        when(eventRepository.saveAndFlush(refEq(inQueueSagaEvent(txId, id), excludedFields)))
            .thenReturn(inQueueSagaEvent(txId, id), inQueueSagaEvent(txId, id), inQueueSagaEvent(txId, id));
        when(eventRepository.save(refEq(failedSagaEvent(txId, id), excludedFields))).thenReturn(failedSagaEvent(txId, id));

        //return new event object each time for state transition verifying
        when(eventRepository.findById(eq(id))).thenReturn(Optional.of(onRetrySagaEvent(txId, id)),
            Optional.of(onRetrySagaEvent(txId, id)),
            Optional.of(onRetrySagaEvent(txId, id)),
            Optional.of(failedSagaEvent(txId, id)));

        SagaTaskSpec task = sagaSpecService.getTransactionSpec(TYPE).getTask(FIRST_TASK_KEY);

        Mockito.doAnswer(invocation -> {
            SagaEvent event = (SagaEvent) invocation.getArguments()[0];
            event.setRetryNumber(maxRetryCount - countDownLatch.getCount() + 1);
            retryService.retry(event, transaction, task, ON_RETRY);
            countDownLatch.countDown();
            return event;
        }).when(eventsSender).sendEvent(refEq(inQueueSagaEvent(txId, id), excludedFields));

        retryService.retry(sagaEvent, transaction, task, ON_RETRY);
        countDownLatch.await(5, TimeUnit.SECONDS);

        verify(eventRepository, atLeastOnce()).findByStatus(any());

        //3 time - in resendEvent; 1 time after retryLimitExceeded
        verify(eventRepository, times(4)).findById(eq(id));

        //verify that event was saved with state IN_QUEUE 3 times
        verify(eventRepository, times(3)).saveAndFlush(
            refEq(inQueueSagaEvent(txId, id), "backOff", "taskContext", "retryNumber", "createDate"));

        //verify that event was saved with state ON_RETRY 3 times
        verify(eventRepository, times(3)).saveAndFlush(
            refEq(onRetrySagaEvent(txId, id), "backOff", "taskContext", "retryNumber", "createDate"));

        //verify that event was saved with state FAILED after retryLimitExceeded
        verify(eventRepository).save(refEq(failedSagaEvent(txId, id), "backOff", "taskContext", "retryNumber", "createDate"));

        verify(eventsSender, times(3)).sendEvent(any());

        //verify that TX was saved with state NEW 3 times
        verify(transactionRepository, times(3)).save(newSagaTransaction(txId));
        //verify that TX was saved with state FAILED after retryLimitExceeded
        verify(transactionRepository).save(failedSagaTransaction(txId));

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

    private SagaEvent newSagaEvent(String txId, String id) {
        return new SagaEvent().setTenantKey(TENANT)
            .setId(id)
            .setTypeKey(FIRST_TASK_KEY)
            .setTransactionId(txId)
            .setCreateDate(Instant.now())
            .setTaskContext(new HashMap<>());
    }

    private SagaEvent failedSagaEvent(String txId, String id) {
        return newSagaEvent(txId, id)
            .setStatus(FAILED);
    }

    private SagaEvent onRetrySagaEvent(String txId, String id) {
        return newSagaEvent(txId, id)
            .setStatus(ON_RETRY);
    }

    private SagaTransaction newSagaTransaction(String txId) {
        return mockTx().setId(txId).setSagaTransactionState(SagaTransactionState.NEW);
    }

    private SagaTransaction failedSagaTransaction(String txId) {
        return mockTx().setId(txId).setSagaTransactionState(SagaTransactionState.FAILED);
    }

    private SagaTransaction canceledSagaTransaction(String txId) {
        return mockTx().setId(txId).setSagaTransactionState(SagaTransactionState.CANCELED);
    }

    @SneakyThrows
    public static String loadFile(String path) {
        InputStream cfgInputStream = new ClassPathResource(path).getInputStream();
        return IOUtils.toString(cfgInputStream, UTF_8);
    }

    private SagaTransaction mockTx(String txId) {
        return new SagaTransaction()
            .setId(txId)
            .setKey("KEY")
            .setTypeKey(TYPE_KEY)
            .setSagaTransactionState(SagaTransactionState.NEW);
    }

    private SagaTransaction mockTx() {
        return mockTx(null);
    }

    @Test
    @SneakyThrows
    public void testRetryWithoutTaskResolver() {
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

        //create new TX object for state transition verifying
        when(transactionRepository.findById(eq(txId)))
            .thenReturn(Optional.of(mockTx(txId)), Optional.of(mockTx(txId)),
                Optional.of(mockTx(txId)), Optional.of(mockTx(txId)),
                Optional.of(mockTx(txId)), Optional.of(mockTx(txId)),
                Optional.of(mockTx(txId)));

        final String[] excludedFields = new String[]{"backOff", "taskContext", "createDate", "retryNumber", "status"};

        when(eventRepository.saveAndFlush(refEq(sagaEvent, excludedFields))).thenReturn(sagaEvent);
        when(eventRepository.findById(eq(id))).thenReturn(Optional.of(sagaEvent));

        SagaTaskSpec task = sagaSpecService.getTransactionSpec(TYPE).getTask(FIRST_TASK_KEY);


        Mockito.doAnswer(invocation -> {
            SagaEvent event = (SagaEvent) invocation.getArguments()[0];
            retryService.retry(event, mockTx(txId), task, ON_RETRY);
            countDownLatch.countDown();
            return event;
        }).when(eventsSender).sendEvent(refEq(sagaEvent, excludedFields));

        retryService.retry(sagaEvent, mockTx(txId), task, ON_RETRY);
        countDownLatch.await(5, TimeUnit.SECONDS);

        assertThat(sagaEvent.getTaskContext(), IsMapContaining.hasEntry("test", "data"));

        verify(transactionRepository, times(3)).save(newSagaTransaction(txId));
        verify(transactionRepository).save(failedSagaTransaction(txId));
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

        //create new TX object for state transition verifying
        when(transactionRepository.findById(eq(txId)))
            .thenReturn(Optional.of(mockTx(txId)), Optional.of(mockTx(txId)),
                Optional.of(mockTx(txId)), Optional.of(mockTx(txId)),
                Optional.of(mockTx(txId)), Optional.of(mockTx(txId)),
                Optional.of(mockTx(txId)));

        final String[] excludedFields = new String[]{"backOff", "taskContext", "createDate", "retryNumber", "status"};

        when(eventRepository.saveAndFlush(refEq(sagaEvent, excludedFields))).thenReturn(sagaEvent);
        when(eventRepository.findById(eq(id))).thenReturn(Optional.of(sagaEvent));

        SagaTaskSpec task = sagaSpecService.getTransactionSpec(TYPE).getTask(FIRST_TASK_KEY);
        Mockito.doAnswer(invocation -> {
            SagaEvent event = (SagaEvent) invocation.getArguments()[0];
            retryService.retry(event, mockTx(txId), task, ON_RETRY);
            countDownLatch.countDown();
            return event;
        }).when(eventsSender).sendEvent(refEq(sagaEvent, excludedFields));

        retryService.retry(sagaEvent, mockTx(txId), task, ON_RETRY);
        countDownLatch.await(5, TimeUnit.SECONDS);

        assertThat(sagaEvent.getTaskContext(), IsMapContaining.hasEntry("test", "data"));

        verify(transactionRepository, times(3)).save(newSagaTransaction(txId));
        verify(transactionRepository).save(failedSagaTransaction(txId));
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
        when(transactionRepository.findById(eq(txId))).thenReturn(Optional.of(mockTx().setId(txId)));

        retryService.doResend(sagaEvent);

        verify(eventRepository).findById(eq(id));
        verifyNoMoreInteractions(eventsSender);

        final String[] excludedFields = new String[]{"backOff", "taskContext", "createDate", "retryNumber"};
        when(eventRepository.saveAndFlush(refEq(sagaEvent, excludedFields))).thenReturn(sagaEvent);

        retryService.doResendInQueueEvent(sagaEvent);

        verify(eventRepository, times(2)).findById(eq(id));
        verify(eventRepository).saveAndFlush(refEq(sagaEvent, excludedFields));
        verify(eventsSender).sendEvent(refEq(sagaEvent, excludedFields));

        verifyNoMoreInteractions(eventsSender);
        verifyNoMoreInteractions(eventRepository);
    }

    @Test
    @SneakyThrows
    public void shouldNotRetryIfTrxWasCanceled() {
        lepResourceLoader.onRefresh("/config/tenants/XM/activation/lep/service/retry/RetryLimitExceeded$$around.groovy", loadFile("/lep/RetryLimitExceeded$$around.groovy"));

        final String txId = UUID.randomUUID().toString();
        final String id = UUID.randomUUID().toString();
        final SagaTransaction transaction = mockTx().setId(txId);
        final int maxRetryCount = 3;
        CountDownLatch countDownLatch = new CountDownLatch(maxRetryCount);

        SagaEvent sagaEvent = newSagaEvent(txId, id);

        //return new TX object each time for state transition verifying
        when(transactionRepository.findById(eq(txId))).thenReturn(
            Optional.of(newSagaTransaction(txId)),
            Optional.of(newSagaTransaction(txId)),
            Optional.of(canceledSagaTransaction(txId)));

        final String[] excludedFields = new String[]{"backOff", "taskContext", "createDate", "retryNumber"};

        //return new event object each time for state transition verifying
        when(eventRepository.saveAndFlush(refEq(onRetrySagaEvent(txId, id), excludedFields)))
            .thenReturn(onRetrySagaEvent(txId, id), onRetrySagaEvent(txId, id));
        when(eventRepository.saveAndFlush(refEq(inQueueSagaEvent(txId, id), excludedFields)))
            .thenReturn(inQueueSagaEvent(txId, id));

        //return new event object each time for state transition verifying
        when(eventRepository.findById(eq(id))).thenReturn(
            Optional.of(onRetrySagaEvent(txId, id)),
            Optional.of(onRetrySagaEvent(txId, id)));

        SagaTaskSpec task = sagaSpecService.getTransactionSpec(TYPE).getTask(FIRST_TASK_KEY);

        Mockito.doAnswer(invocation -> {
            SagaEvent event = (SagaEvent) invocation.getArguments()[0];
            event.setRetryNumber(maxRetryCount - countDownLatch.getCount() + 1);
            retryService.retry(event, transaction, task, ON_RETRY);
            countDownLatch.countDown();
            return event;
        }).when(eventsSender).sendEvent(refEq(inQueueSagaEvent(txId, id), excludedFields));

        retryService.retry(sagaEvent, transaction, task, ON_RETRY);
        countDownLatch.await(5, TimeUnit.SECONDS);

        verify(eventRepository, times(2)).findById(eq(id));

        verify(eventRepository, times(1)).saveAndFlush(
            refEq(inQueueSagaEvent(txId, id), "backOff", "taskContext", "retryNumber", "createDate"));

        verify(eventRepository, times(2)).saveAndFlush(
            refEq(onRetrySagaEvent(txId, id), "backOff", "taskContext", "retryNumber", "createDate"));

        verify(eventsSender, times(1)).sendEvent(any());
        //verify that TX was saved with state NEW 1 times
        verify(transactionRepository, times(1)).save(newSagaTransaction(txId));

        verifyNoMoreInteractions(eventsSender);
        verifyNoMoreInteractions(eventRepository);
    }

}
