package com.icthh.xm.tmf.ms.activation.service;

import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.IN_QUEUE;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.SUSPENDED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_END;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_START;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.CANCELED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.FINISHED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy.RETRY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState;
import com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaLogRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.io.ClassPathResource;

@RunWith(MockitoJUnitRunner.class)
public class SagaServiceTest {

    public static final String TEST_TYPE_KEY = "TEST-SAGA-TYPE-KEY";
    public static final String MOCK_KEY = "mockKey";

    private SagaServiceImpl sagaService;

    private SagaSpecService specService;
    @Mock
    private SagaLogRepository logRepository;
    @Mock
    private SagaTransactionRepository transactionRepository;
    @Mock
    private EventsSender eventsManager;
    @Mock
    private TenantUtils tenantUtils;
    @Mock
    private SagaTaskExecutor taskExecutor;
    @Mock
    private RetryService retryService;
    @Mock
    private SagaEventRepository sagaEventRepository;

    private Clock clock = Clock.fixed(Instant.now(), UTC);

    private List<String> allTasks = asList("FIRST-PARALEL-TASK", "PARALEL-TASK1", "PARALEL-TASK2", "NEXT-JOIN-TASK",
        "SECOND-PARALEL-TASK", "SOME-OTHER-TASK");

    private static final RetryPolicy RETRY_POLICY_FROM_TRANSACTION = RETRY;
    private static final Integer DEFAULT_TRANSACTION_BACK_OFF = 5;
    private static final Long RETRY_COUNT_FROM_TASK = -1L;
    private static final Integer MAX_BACK_OFF_FROM_TASK = 30;

    @Before
    public void before() throws IOException {
        specService = new SagaSpecService(tenantUtils);
        sagaService = new SagaServiceImpl(logRepository, transactionRepository, specService, eventsManager,
            tenantUtils, taskExecutor, retryService, sagaEventRepository);
        sagaService.setClock(clock);
        specService.onRefresh("/config/tenants/XM/activation/activation-spec.yml", loadFile("spec/activation-spec.yml"));
    }

    public static String loadFile(String path) throws IOException {
        return IOUtils.toString(new ClassPathResource(path).getInputStream(), UTF_8);
    }

    @Test
    public void testGenerageFirstEventOnCreateSagaTx() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        when(transactionRepository.save(refEq(mockTx()))).thenReturn(mockTx(txId));
        when(sagaEventRepository.save(refEq(inQueueEvent(txId, "FIRST-PARALEL-TASK", null), "id")))
            .thenReturn(inQueueEvent(txId, "FIRST-PARALEL-TASK", "savedFirstTaskId"));
        when(sagaEventRepository.save(refEq(inQueueEvent(txId, "SECOND-PARALEL-TASK", null), "id")))
            .thenReturn(inQueueEvent(txId, "SECOND-PARALEL-TASK", "savedSecondTaskId"));

        sagaService.createNewSaga(mockTx());

        verify(transactionRepository).findByKey(anyString());
        verify(transactionRepository).save(refEq(mockTx()));
        verify(sagaEventRepository).save(refEq(inQueueEvent(txId, "FIRST-PARALEL-TASK", null), "id"));
        verify(sagaEventRepository).save(refEq(inQueueEvent(txId, "SECOND-PARALEL-TASK", null), "id"));
        verify(eventsManager).sendEvent(refEq(inQueueEvent(txId, "FIRST-PARALEL-TASK", "savedFirstTaskId")));
        verify(eventsManager).sendEvent(refEq(inQueueEvent(txId, "SECOND-PARALEL-TASK", "savedSecondTaskId")));

        noMoreInteraction();
    }

    @Test
    public void resendEventWhenCreationSagaTxNotFinished() {
        String txId = UUID.randomUUID().toString();
        when(transactionRepository.findById(txId)).thenReturn(empty());

        sagaService.onSagaEvent(new SagaEvent().setTenantKey("XM")
            .setTypeKey("FIRST-PARALEL-TASK")
            .setTransactionId(txId));

        verify(transactionRepository).findById(eq(txId));
        verify(eventsManager).resendEvent(refEq(new SagaEvent().setTenantKey("XM")
            .setTypeKey("FIRST-PARALEL-TASK")
            .setTransactionId(txId), "id"));

        noMoreInteraction();
    }

    @Test
    public void skipEventWhenTransactionCanceled() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, CANCELED)));

        sagaService.onSagaEvent(new SagaEvent().setTenantKey("XM")
            .setTypeKey("FIRST-PARALEL-TASK")
            .setTransactionId(txId));

        verify(transactionRepository).findById(eq(txId));

        noMoreInteraction();
    }

    @Test
    public void skipEventWhenEventFinishedButNotUpdateTxStateWhenTxHaveNotFinishedTasks() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();

        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));
        when(logRepository.getFinishLogs(eq(txId), eq(asList("FIRST-PARALEL-TASK"))))
            .thenReturn(asList(new SagaLog().setEventTypeKey("FIRST-PARALEL-TASK").setLogType(EVENT_END)));
        when(logRepository.getFinishLogs(eq(txId), eq(allTasks)))
            .thenReturn(asList(new SagaLog().setEventTypeKey("FIRST-PARALEL-TASK").setLogType(EVENT_END)));

        sagaService.onSagaEvent(new SagaEvent().setTenantKey("XM")
            .setTypeKey("FIRST-PARALEL-TASK")
            .setTransactionId(txId));

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).getFinishLogs(eq(txId), eq(asList("FIRST-PARALEL-TASK")));
        verify(logRepository).getFinishLogs(eq(txId), eq(allTasks));

        noMoreInteraction();
    }


    @Test
    public void skipEventWhenEventFinishedAndUpdateTxStateWhenAllTasksFinished() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();

        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));
        when(logRepository.getFinishLogs(eq(txId), eq(asList("FIRST-PARALEL-TASK"))))
            .thenReturn(asList(new SagaLog().setEventTypeKey("FIRST-PARALEL-TASK").setLogType(EVENT_END)));
        when(logRepository.getFinishLogs(eq(txId), eq(allTasks)))
            .thenReturn(allTasks.stream().map(key -> new SagaLog().setEventTypeKey(key).setLogType(EVENT_END)).collect(toList()));

        sagaService.onSagaEvent(new SagaEvent().setTenantKey("XM")
            .setTypeKey("FIRST-PARALEL-TASK")
            .setTransactionId(txId));

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).getFinishLogs(eq(txId), eq(asList("FIRST-PARALEL-TASK")));
        verify(logRepository).getFinishLogs(eq(txId), eq(allTasks));
        verify(transactionRepository).save(refEq(mockTx(txId, FINISHED)));
        verify(taskExecutor).onFinish(refEq(mockTx(txId, FINISHED)));

        noMoreInteraction();
    }

    @Test
    public void resendEventIfNotAllDependsTaskFinished() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();

        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));
        when(logRepository.getFinishLogs(eq(txId), eq(asList("NEXT-JOIN-TASK")))).thenReturn(emptyList());
        when(logRepository.getFinishLogs(eq(txId), eq(asList("PARALEL-TASK1", "PARALEL-TASK2"))))
            .thenReturn(asList(new SagaLog().setEventTypeKey("PARALEL-TASK1").setLogType(EVENT_END)));

        sagaService.onSagaEvent(new SagaEvent().setTenantKey("XM")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId));

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).getFinishLogs(eq(txId), eq(asList("NEXT-JOIN-TASK")));
        verify(logRepository).getFinishLogs(eq(txId), eq(asList("PARALEL-TASK1", "PARALEL-TASK2")));
        verify(retryService).retryForWaitDependsTask(refEq(new SagaEvent().setTenantKey("XM")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId), "id"), any());

        noMoreInteraction();
    }

    @Test
    public void successExecuteTaskAndSendNextEvents() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        SagaTaskSpec sagaTaskSpec = sagaTaskSpec();
        Continuation continuation = new Continuation();

        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));
        when(logRepository.getFinishLogs(eq(txId), eq(asList("NEXT-JOIN-TASK")))).thenReturn(emptyList());
        when(logRepository.getFinishLogs(eq(txId), eq(asList("PARALEL-TASK1", "PARALEL-TASK2"))))
            .thenReturn(asList(new SagaLog().setEventTypeKey("PARALEL-TASK1").setLogType(EVENT_END),
                new SagaLog().setEventTypeKey("PARALEL-TASK2").setLogType(EVENT_END)));
        when(sagaEventRepository.save(refEq(inQueueEvent(txId, "SOME-OTHER-TASK", null), "id")))
            .thenReturn(inQueueEvent(txId, "SOME-OTHER-TASK", "saved-event" + txId));
        when(sagaEventRepository.findById("saved-event" + txId))
            .thenReturn(Optional.of(inQueueEvent(txId, "NEXT-JOIN-TASK", "saved-event" + txId)));
        when(sagaEventRepository.existsById("saved-event" + txId)).thenReturn(true);

        SagaEvent sagaEvent = mockEvent(txId, "NEXT-JOIN-TASK", "saved-event" + txId);
        sagaService.onSagaEvent(sagaEvent);

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).getFinishLogs(eq(txId), eq(asList("NEXT-JOIN-TASK")));
        verify(logRepository).getFinishLogs(eq(txId), eq(asList("PARALEL-TASK1", "PARALEL-TASK2")));
        verify(logRepository).findLogs(eq(EVENT_START), eq(mockTx(txId, NEW)), eq("NEXT-JOIN-TASK"));
        verify(logRepository).save(refEq(createLog(txId, "NEXT-JOIN-TASK", EVENT_START)));
        verify(taskExecutor).executeTask(refEq(sagaTaskSpec), refEq(sagaEvent, "id"), refEq(mockTx(txId)), refEq(continuation));
        verify(sagaEventRepository).findById("saved-event" + txId);
        verify(sagaEventRepository).existsById("saved-event" + txId);
        verify(sagaEventRepository).deleteById("saved-event" + txId);
        verify(sagaEventRepository).save(refEq(inQueueEvent(txId, "SOME-OTHER-TASK", null), "id"));
        verify(eventsManager).sendEvent(refEq(inQueueEvent(txId, "SOME-OTHER-TASK", "saved-event" + txId)));
        verify(logRepository).findLogs(eq(EVENT_END), eq(mockTx(txId, NEW)), eq("NEXT-JOIN-TASK"));
        verify(logRepository).save(refEq(createLog(txId, "NEXT-JOIN-TASK", EVENT_END)));
        verify(logRepository).getFinishLogs(eq(txId), eq(allTasks));

        noMoreInteraction();
    }

    private SagaEvent mockEvent(String txId, String typeKey, String id) {
        return new SagaEvent().setTenantKey("XM")
                              .setId(id)
                              .setTypeKey(typeKey)
                              .setCreateDate(Instant.now(clock))
                              .setTransactionId(txId);
    }

    private SagaEvent inQueueEvent(String txId, String typeKey, String id) {
        return new SagaEvent().setTenantKey("XM")
                              .setId(id)
                              .setStatus(IN_QUEUE)
                              .setTypeKey(typeKey)
                              .setCreateDate(Instant.now(clock))
                              .setTransactionId(txId);
    }

    private SagaTaskSpec sagaTaskSpec() {
        return new SagaTaskSpec()
                .setBackOff(5).setMaxBackOff(30)
                .setDepends(asList("PARALEL-TASK1", "PARALEL-TASK2"))
                .setKey("NEXT-JOIN-TASK")
                .setRetryCount(-1L)
                .setRetryPolicy(RETRY)
                .setNext(asList("SOME-OTHER-TASK"));
    }

    private SagaLog createLog(String txId, String eventTypeKey, SagaLogType eventStart) {
        return new SagaLog().setEventTypeKey(eventTypeKey).setCreateDate(Instant.now(clock))
            .setLogType(eventStart).setSagaTransaction(mockTx(txId));
    }

    @Test
    public void failedExecuteTaskAndResendEvent() {

        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        SagaTaskSpec sagaTaskSpec = sagaTaskSpec();
        Continuation continuation = new Continuation();

        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));
        when(logRepository.getFinishLogs(eq(txId), eq(asList("NEXT-JOIN-TASK")))).thenReturn(emptyList());
        when(logRepository.getFinishLogs(eq(txId), eq(asList("PARALEL-TASK1", "PARALEL-TASK2"))))
            .thenReturn(asList(new SagaLog().setEventTypeKey("PARALEL-TASK1").setLogType(EVENT_END),
                new SagaLog().setEventTypeKey("PARALEL-TASK2").setLogType(EVENT_END)));
        doThrow(new RuntimeException("TEST EXCEPTION")).when(taskExecutor)
            .executeTask(refEq(sagaTaskSpec), any(), refEq(mockTx(txId)), refEq(continuation));

        SagaEvent sagaEvent = new SagaEvent().setTenantKey("XM")
            .setId("eventId")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId);

        when(sagaEventRepository.findById("eventId")).thenReturn(Optional.of(sagaEvent));

        sagaService.onSagaEvent(sagaEvent);

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).getFinishLogs(eq(txId), eq(asList("NEXT-JOIN-TASK")));
        verify(logRepository).getFinishLogs(eq(txId), eq(asList("PARALEL-TASK1", "PARALEL-TASK2")));
        verify(logRepository).findLogs(eq(EVENT_START), eq(mockTx(txId, NEW)), eq("NEXT-JOIN-TASK"));
        verify(logRepository).save(refEq(createLog(txId, "NEXT-JOIN-TASK", EVENT_START)));
        verify(taskExecutor).executeTask(refEq(sagaTaskSpec), refEq(sagaEvent), refEq(mockTx(txId)), refEq(continuation));
        verify(retryService).retry(refEq(new SagaEvent().setTenantKey("XM")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId), "id"), any());
        verify(sagaEventRepository).findById("eventId");

        noMoreInteraction();
    }

    @Test
    public void successExecuteTaskAndFinishTransaction() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        SagaTaskSpec sagaTaskSpec = sagaTaskSpec().setKey("SOME-OTHER-TASK").setDepends(emptyList()).setNext(emptyList());
        Continuation continuation = new Continuation();

        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));
        when(logRepository.getFinishLogs(eq(txId), eq(asList("SOME-OTHER-TASK")))).thenReturn(emptyList());
        when(logRepository.getFinishLogs(eq(txId), eq(allTasks)))
            .thenReturn(allTasks.stream().map(key -> new SagaLog()
                .setCreateDate(Instant.now(clock)).setEventTypeKey(key).setLogType(EVENT_END)).collect(toList()));

        SagaEvent sagaEvent = new SagaEvent().setId("eventId").setTenantKey("XM")
            .setTypeKey("SOME-OTHER-TASK")
            .setTransactionId(txId);

        when(sagaEventRepository.findById("eventId")).thenReturn(Optional.of(sagaEvent));
        when(sagaEventRepository.existsById("eventId")).thenReturn(true);

        sagaService.onSagaEvent(sagaEvent);

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).getFinishLogs(eq(txId), eq(asList("SOME-OTHER-TASK")));
        verify(logRepository).findLogs(eq(EVENT_START), refEq(mockTx(txId, NEW), "sagaTransactionState"), eq("SOME-OTHER-TASK"));
        verify(logRepository).save(refEq(createLog(txId, "SOME-OTHER-TASK", EVENT_START), "sagaTransaction"));
        verify(taskExecutor).executeTask(refEq(sagaTaskSpec), refEq(sagaEvent), refEq(mockTx(txId), "sagaTransactionState"), refEq(continuation));
        verify(logRepository).findLogs(eq(EVENT_END), refEq(mockTx(txId, NEW), "sagaTransactionState"), eq("SOME-OTHER-TASK"));
        verify(logRepository).save(refEq(createLog(txId, "SOME-OTHER-TASK", EVENT_END), "sagaTransaction"));
        verify(logRepository).getFinishLogs(eq(txId), eq(allTasks));
        verify(transactionRepository).save(refEq(mockTx(txId, FINISHED)));
        verify(taskExecutor).onFinish(refEq(mockTx(txId, FINISHED)));
        verify(sagaEventRepository).findById("eventId");
        verify(sagaEventRepository).existsById("eventId");
        verify(sagaEventRepository).deleteById("eventId");

        noMoreInteraction();
    }

    @Test
    public void setConfigurationFromTransaction() throws IOException{
        specService.onRefresh("/config/tenants/XM/activation/activation-spec.yml", loadFile("spec/activation-spec-task-configuration.yml"));
        when(tenantUtils.getTenantKey()).thenReturn("XM");

        SagaTransactionSpec transactionSpecWithConfiguration = specService.getTransactionSpec("TEST-TYPE-KEY");
        SagaTaskSpec task = transactionSpecWithConfiguration.getTask("TASK-1");
        assertEquals(RETRY_POLICY_FROM_TRANSACTION, task.getRetryPolicy());
        assertEquals(RETRY_COUNT_FROM_TASK, task.getRetryCount());
        assertEquals(DEFAULT_TRANSACTION_BACK_OFF, task.getBackOff());
        assertEquals(MAX_BACK_OFF_FROM_TASK, task.getMaxBackOff());
    }

    @Test
    @SneakyThrows
    public void testTaskDeduplication() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txTypeKey = "TASK-AND-TASK-WITH-SUSPEND-TX";
        String txId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        CountDownLatch latch = new CountDownLatch(1);
        String eventTypeKey = "SIMPLE-TASK";
        SagaEvent sagaEvent = mockEvent(txId, eventTypeKey, taskId);
        Continuation continuation = new Continuation();

        SagaTransaction transaction = mockTx(txId, NEW).setTypeKey(txTypeKey);
        SagaTaskSpec taskSpec = specService.getTransactionSpec(txTypeKey).getTask(eventTypeKey);

        when(transactionRepository.findById(txId)).thenReturn(of(transaction));
        when(logRepository.getFinishLogs(eq(txId), eq(asList(eventTypeKey)))).thenReturn(emptyList());
        when(taskExecutor.executeTask(taskSpec, sagaEvent, transaction, continuation))
            .then(it -> { latch.await(2, SECONDS); return emptyMap();});
        when(sagaEventRepository.findById(sagaEvent.getId())).thenReturn(Optional.of(sagaEvent));
        when(sagaEventRepository.existsById(sagaEvent.getId())).thenReturn(true);

        Thread t1 = new Thread(() -> sagaService.onSagaEvent(sagaEvent));
        t1.start();
        Thread t2 = new Thread(() -> sagaService.onSagaEvent(mockEvent(txId, eventTypeKey, taskId)));
        t2.start();

        t1.join();
        t2.join();

        latch.countDown();

        verify(transactionRepository, times(2)).findById(txId);
        verify(logRepository, times(2)).getFinishLogs(eq(txId), eq(asList(eventTypeKey)));
        verify(sagaEventRepository, times(2)).findById(sagaEvent.getId());
        verify(taskExecutor).executeTask(taskSpec, sagaEvent, transaction, continuation);
        verify(logRepository).findLogs(eq(EVENT_START), refEq(transaction), eq(sagaEvent.getTypeKey()));
        verify(logRepository).save(refEq(createLog(txId, sagaEvent.getTypeKey(), EVENT_START), "sagaTransaction"));
        verify(logRepository).findLogs(eq(EVENT_END), refEq(transaction), eq(sagaEvent.getTypeKey()));
        verify(logRepository).save(refEq(createLog(txId, sagaEvent.getTypeKey(), EVENT_END), "sagaTransaction"));
        verify(sagaEventRepository).existsById(sagaEvent.getId());
        verify(sagaEventRepository).deleteById(sagaEvent.getId());
        verify(logRepository).getFinishLogs(eq(txId), eq(asList(eventTypeKey, "SUSPEND-TASK")));

        noMoreInteraction();
    }

    @Test
    @SneakyThrows
    public void testTaskDeduplicationSuspendedTask() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txTypeKey = "TASK-AND-TASK-WITH-SUSPEND-TX";
        String txId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        String eventTypeKey = "SUSPEND-TASK";
        SagaEvent sagaEvent = mockEvent(txId, eventTypeKey, taskId);
        Continuation continuation = new Continuation();

        SagaTransaction transaction = mockTx(txId, NEW).setTypeKey(txTypeKey);
        SagaTaskSpec taskSpec = specService.getTransactionSpec(txTypeKey).getTask(eventTypeKey);

        when(transactionRepository.findById(txId)).thenReturn(of(transaction));
        when(logRepository.getFinishLogs(eq(txId), eq(asList(eventTypeKey)))).thenReturn(emptyList());
        when(taskExecutor.executeTask(taskSpec, sagaEvent, transaction, continuation)).thenReturn(emptyMap());
        when(sagaEventRepository.findById(sagaEvent.getId())).thenReturn(Optional.of(sagaEvent));
        when(sagaEventRepository.existsById(sagaEvent.getId())).thenReturn(true);

        sagaService.onSagaEvent(sagaEvent);
        sagaService.onSagaEvent(sagaEvent);

        verify(transactionRepository, times(2)).findById(txId);
        verify(logRepository, times(2)).getFinishLogs(eq(txId), eq(asList(eventTypeKey)));
        verify(sagaEventRepository, times(2)).findById(sagaEvent.getId());
        verify(taskExecutor).executeTask(taskSpec, sagaEvent, transaction, continuation);
        verify(logRepository).findLogs(eq(EVENT_START), refEq(transaction), eq(sagaEvent.getTypeKey()));
        verify(logRepository).save(refEq(createLog(txId, sagaEvent.getTypeKey(), EVENT_START), "sagaTransaction"));
        verify(sagaEventRepository).save(mockEvent(txId, eventTypeKey, taskId).setStatus(SUSPENDED));

        noMoreInteraction();
    }

    @Test
    @SneakyThrows
    public void testTransactionDeduplication() {

    }

    private SagaTransaction mockTx() {
        return new SagaTransaction()
            .setKey(MOCK_KEY)
            .setTypeKey(TEST_TYPE_KEY)
            .setCreateDate(Instant.now(clock))
            .setSagaTransactionState(NEW);
    }

    private SagaTransaction mockTx(String id) {
        return mockTx().setId(id).setKey(id);
    }

    private SagaTransaction mockTx(SagaTransactionState state) {
        return mockTx().setSagaTransactionState(state);
    }

    private SagaTransaction mockTx(String id, SagaTransactionState state) {
        return mockTx().setSagaTransactionState(state).setId(id).setKey(id);
    }

    public void noMoreInteraction() {
        Mockito.verifyNoMoreInteractions(logRepository);
        Mockito.verifyNoMoreInteractions(transactionRepository);
        Mockito.verifyNoMoreInteractions(sagaEventRepository);
        Mockito.verifyNoMoreInteractions(eventsManager);
        Mockito.verifyNoMoreInteractions(taskExecutor);
    }


}
