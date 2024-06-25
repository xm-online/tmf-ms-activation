package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.commons.exceptions.BusinessException;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaEventError;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState;
import com.icthh.xm.tmf.ms.activation.domain.spec.DependsStrategy;
import com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaLogRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.shaded.org.apache.commons.lang.mutable.MutableBoolean;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static com.icthh.xm.tmf.ms.activation.config.Constants.GENERAL_ERROR_CODE;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.IN_QUEUE;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.SUSPENDED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_END;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_START;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.REJECTED_BY_CONDITION;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.CANCELED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.FINISHED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static com.icthh.xm.tmf.ms.activation.domain.spec.MockSagaType.fromTypeKey;
import static com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy.RETRY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SagaServiceUnitTest {

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
    @Captor
    private ArgumentCaptor<SagaLog> sagaLogArgumentCaptor;
    @Captor
    private ArgumentCaptor<String> sagaTransactionIdCaptor;
    @Captor
    private ArgumentCaptor<String> sagaEventIdCaptor;
    @Captor
    private ArgumentCaptor<String> sagaEventToDeleteIdCaptor;
    @Captor
    private ArgumentCaptor<String> sagaEventTypeKeyCaptor;

    private Clock clock = Clock.fixed(Instant.now(), UTC);

    private List<String> allTasks = List.of("FIRST-PARALEL-TASK", "PARALEL-TASK1", "PARALEL-TASK2", "NEXT-JOIN-TASK",
        "SECOND-PARALEL-TASK", "SOME-OTHER-TASK");

    private static final RetryPolicy RETRY_POLICY_FROM_TRANSACTION = RETRY;
    private static final Integer DEFAULT_TRANSACTION_BACK_OFF = 5;
    private static final Long RETRY_COUNT_FROM_TASK = -1L;
    private static final Integer MAX_BACK_OFF_FROM_TASK = 30;

    @Before
    public void before() throws IOException {
        specService = new SagaSpecService(tenantUtils, new MapSpecResolver());
        var transactionStatusStrategy = new FinishTransactionStrategy(taskExecutor, transactionRepository, logRepository);
        sagaService = new SagaServiceImpl(logRepository, transactionRepository, specService, eventsManager,
            tenantUtils, taskExecutor, retryService, sagaEventRepository, transactionStatusStrategy);
        sagaService.setClock(clock);
        sagaService.setSelf(sagaService);
        specService.onRefresh("/config/tenants/XM/activation/activation-spec.yml", loadFile("spec/activation-spec.yml"));
        when(taskExecutor.onCheckWaitCondition(any(), any(), any())).thenReturn(true);
    }

    public static String loadFile(String path) throws IOException {
        return IOUtils.toString(new ClassPathResource(path).getInputStream(), UTF_8);
    }

    @Test
    public void testGenerageFirstEventOnCreateSagaTx() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        when(transactionRepository.saveAndFlush(refEq(mockTx()))).thenReturn(mockTx(txId));
        when(sagaEventRepository.saveAndFlush(refEq(inQueueEvent(txId, "FIRST-PARALEL-TASK", null, null), "id")))
            .thenReturn(inQueueEvent(txId, "FIRST-PARALEL-TASK", "savedFirstTaskId", null));
        when(sagaEventRepository.saveAndFlush(refEq(inQueueEvent(txId, "SECOND-PARALEL-TASK", null, null), "id")))
            .thenReturn(inQueueEvent(txId, "SECOND-PARALEL-TASK", "savedSecondTaskId", null));

        sagaService.createNewSaga(mockTx());

        verify(transactionRepository).findByKey(anyString());
        verify(transactionRepository).saveAndFlush(refEq(mockTx()));
        verify(sagaEventRepository).saveAndFlush(refEq(inQueueEvent(txId, "FIRST-PARALEL-TASK", null, null), "id"));
        verify(sagaEventRepository).saveAndFlush(refEq(inQueueEvent(txId, "SECOND-PARALEL-TASK", null, null), "id"));
        verify(eventsManager).sendEvent(refEq(inQueueEvent(txId, "FIRST-PARALEL-TASK", "savedFirstTaskId", null)));
        verify(eventsManager).sendEvent(refEq(inQueueEvent(txId, "SECOND-PARALEL-TASK", "savedSecondTaskId", null)));

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
    public void testRejectTasksByCondition() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();

        String typeKey = "TASK-WITH-REJECTED-BY-CONDITION-TASKS";
        String firstTaskKey = "FIRST-TASK";

        SagaTransaction transaction = mockTx(txId, NEW).setTypeKey(typeKey);
        when(transactionRepository.findById(txId)).thenReturn(of(transaction));

        SagaTransactionSpec transactionSpec = specService.getTransactionSpec(transaction);
        SagaTaskSpec firstTaskSpec = transactionSpec.getTask(firstTaskKey);

        generateEvent(txId, firstTaskKey, "NEXT-SECOND-TASK", transaction, firstTaskSpec);

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository, times(4)).saveAndFlush(sagaLogArgumentCaptor.capture());

        List<SagaLog> savedLogs = sagaLogArgumentCaptor.getAllValues();

        assertThat(savedLogs, hasSize(4));

        List<SagaLog> started = getByLogType(savedLogs, EVENT_START);
        assertThat(started, hasSize(1));
        assertEquals(started.get(0).getEventTypeKey(), firstTaskKey);

        List<SagaLog> finished = getByLogType(savedLogs, EVENT_END);
        assertThat(finished, hasSize(1));
        assertEquals(started.get(0).getEventTypeKey(), firstTaskKey);

        List<String> rejectedEventTypes = extractEventTypeKey(getByLogType(savedLogs, REJECTED_BY_CONDITION));

        assertThat(rejectedEventTypes, hasSize(2));
        assertThat(rejectedEventTypes, containsInAnyOrder("NEXT-TASK-REJECTED-1", "NEXT-TASK-REJECTED-2"));
    }

    @Test
    public void testPartialRejectTasksByCondition() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();

        String typeKey = "TASK-WITH-REJECTED-AND-NON-REJECTED";

        SagaTransaction transaction = mockTx(txId, NEW).setTypeKey(typeKey);
        when(transactionRepository.findById(txId)).thenReturn(of(transaction));

        SagaTransactionSpec transactionSpec = specService.getTransactionSpec(transaction);

        List<String> startedAndEndedEvents = List.of("FIRST-TASK", "NEXT-SECOND-TASK", "NEXT-THIRD-TASK");
        List<String> nextEvents = List.of("NEXT-SECOND-TASK", "NEXT-THIRD-TASK", StringUtils.EMPTY);

        for (int i = 0; i < startedAndEndedEvents.size(); i++) {
            generateEvent(txId, startedAndEndedEvents.get(i),
                nextEvents.get(i), transaction, transactionSpec.getTask(startedAndEndedEvents.get(i)));
        }

        verify(transactionRepository, times(startedAndEndedEvents.size())).findById(eq(txId));

        //startedAndEndedEvents.size() tasks started and ended + 2 task rejected
        verify(logRepository, times(startedAndEndedEvents.size() * 2 + 2))
            .saveAndFlush(sagaLogArgumentCaptor.capture());

        List<SagaLog> savedLogs = sagaLogArgumentCaptor.getAllValues();

        assertThat(savedLogs, hasSize(startedAndEndedEvents.size() * 2 + 2));

        List<SagaLog> started = getByLogType(savedLogs, EVENT_START);
        assertThat(started, hasSize(startedAndEndedEvents.size()));
        assertEquals(startedAndEndedEvents, extractEventTypeKey(started));

        List<SagaLog> ended = getByLogType(savedLogs, EVENT_END);
        assertThat(ended, hasSize(startedAndEndedEvents.size()));
        assertEquals(startedAndEndedEvents, extractEventTypeKey(ended));

        List<SagaLog> rejected = getByLogType(savedLogs, REJECTED_BY_CONDITION);
        assertThat(rejected, hasSize(2));
        assertThat(List.of("NEXT-REJECTED-TASK", "NEXT-REJECTED-INSIDE-REJECTED-TASK"),
            containsInAnyOrder(extractEventTypeKey(rejected).toArray(new String[rejected.size()])));
    }

    @Test
    public void testRejectTasksByConditionAndDeleteEvents() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();

        String typeKey = "TASK-WITH-REJECTED-BY-CONDITION-TASK-AND-DELETED-EVENT";
        String firstTaskKey = "FIRST-TASK";

        SagaTransaction transaction = mockTx(txId, NEW).setTypeKey(typeKey);
        when(transactionRepository.findById(txId)).thenReturn(of(transaction));
        when(sagaEventRepository.existsById(anyString())).thenReturn(true);

        SagaTransactionSpec transactionSpec = specService.getTransactionSpec(transaction);
        SagaTaskSpec firstTaskSpec = transactionSpec.getTask(firstTaskKey);

        generateEvent(txId, firstTaskKey, "SECOND-TASK", transaction, firstTaskSpec);

        verify(transactionRepository).findById(eq(txId));
        verify(sagaEventRepository, times(2))
            .findByTransactionIdAndTypeKey(sagaTransactionIdCaptor.capture(), sagaEventTypeKeyCaptor.capture());
        verify(sagaEventRepository, times(1)).existsById(sagaEventIdCaptor.capture());
        verify(sagaEventRepository, times(1)).deleteById(sagaEventToDeleteIdCaptor.capture());

        List<String> transactionIds = sagaTransactionIdCaptor.getAllValues();
        assertThat(transactionIds, hasSize(2));
        assertEquals(txId, transactionIds.get(0));

        List<String> eventsToBeDeleted = sagaEventTypeKeyCaptor.getAllValues();
        assertThat(eventsToBeDeleted, hasSize(2));
        assertEquals("REJECTED-TASK", eventsToBeDeleted.get(0));

        List<String> eventIds = sagaEventIdCaptor.getAllValues();
        assertThat(eventIds, hasSize(1));
        List<String> eventsToDeleteIds = sagaEventToDeleteIdCaptor.getAllValues();
        assertThat(eventIds, hasSize(1));
        assertEquals(eventIds.get(0), eventsToDeleteIds.get(0));
    }

    private List<String> extractEventTypeKey(final List<SagaLog> started) {
        return started.stream().map(SagaLog::getEventTypeKey).collect(toList());
    }

    private void generateEvent(final String txId, final String taskKey, final String nextTaskKey,
                               final SagaTransaction transaction, final SagaTaskSpec taskSpec) {
        SagaEvent sagaEvent = new SagaEvent().setTenantKey("XM")
            .setTypeKey(taskKey)
            .setTransactionId(txId);

        when(taskExecutor.executeTask(eq(taskSpec), eq(sagaEvent), eq(transaction), eq(new Continuation())))
            .thenAnswer(invocationOnMock -> {
                    SagaTaskSpec spec = invocationOnMock.getArgument(0);
                    spec.setNext(StringUtils.isEmpty(nextTaskKey) ? emptyList() : List.of(nextTaskKey));
                    return Map.of();
                }
            );

        sagaService.onSagaEvent(sagaEvent);
    }

    private List<SagaLog> getByLogType(List<SagaLog> sagaLogs, SagaLogType logType) {
        return sagaLogs.stream().filter(task -> task.getLogType().equals(logType)).collect(toList());
    }

    @Test
    public void testTransactionFinishedIfTasksRejectedByCondition() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();

        String typeKey = "TASK-WITH-REJECTED-BY-CONDITION-TASKS";
        String firstTaskKey = "FIRST-TASK";

        SagaTransaction transaction = mockTx(txId, NEW).setTypeKey(typeKey);
        when(transactionRepository.findById(txId)).thenReturn(of(transaction));

        SagaEvent sagaEvent = new SagaEvent().setTenantKey("XM")
            .setTypeKey(firstTaskKey)
            .setTransactionId(txId);

        when(logRepository.getFinishLogsTypeKeys(eq(txId),
            eq(List.of("FIRST-TASK", "NEXT-SECOND-TASK", "NEXT-TASK-REJECTED-1", "NEXT-TASK-REJECTED-2"))))
            .thenReturn(List.of(
                "FIRST-TASK",
                "NEXT-SECOND-TASK",
                "NEXT-TASK-REJECTED-1",
                "NEXT-TASK-REJECTED-2"
            ));

        sagaService.onSagaEvent(sagaEvent);

        verify(transactionRepository).saveAndFlush(refEq(transaction.setSagaTransactionState(FINISHED)));
    }

    @Test
    public void skipEventWhenEventFinishedButNotUpdateTxStateWhenTxHaveNotFinishedTasks() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        SagaEvent sagaEvent = new SagaEvent().setTenantKey("XM")
            .setId("SAGA-EVENT-ID")
            .setTypeKey("FIRST-PARALEL-TASK")
            .setTransactionId(txId);

        when(sagaEventRepository.existsById(sagaEvent.getId())).thenReturn(true);
        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));
        when(logRepository.findFinishLogTypeKeyAndIteration(eq(txId), eq("FIRST-PARALEL-TASK"), isNull()))
            .thenReturn(Optional.of(new SagaLog().setEventTypeKey("FIRST-PARALEL-TASK").setLogType(EVENT_END)));
        when(logRepository.getFinishLogsTypeKeys(eq(txId), eq(allTasks)))
            .thenReturn(List.of("FIRST-PARALEL-TASK"));

        sagaService.onSagaEvent(sagaEvent);

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).findFinishLogTypeKeyAndIteration(eq(txId), eq("FIRST-PARALEL-TASK"), isNull());
        verify(logRepository).getFinishLogsTypeKeys(eq(txId), eq(allTasks));
        verify(sagaEventRepository).existsById(sagaEvent.getId());
        verify(sagaEventRepository).deleteById(sagaEvent.getId());

        noMoreInteraction();
    }


    @Test
    public void skipEventWhenEventFinishedAndUpdateTxStateWhenAllTasksFinished() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        Map<String, Object> taskContext = Map.of("TASK-RESULT", "SUCCESS");
        SagaEvent sagaEvent = new SagaEvent().setTenantKey("XM")
            .setId("SAGA-EVENT-ID")
            .setTaskContext(taskContext)
            .setTypeKey("FIRST-PARALEL-TASK")
            .setTransactionId(txId);

        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));

        when(logRepository.findFinishLogTypeKeyAndIteration(eq(txId), eq("FIRST-PARALEL-TASK"), isNull()))
            .thenReturn(Optional.of(new SagaLog().setEventTypeKey("FIRST-PARALEL-TASK").setLogType(EVENT_END)));
        when(logRepository.getFinishLogsTypeKeys(eq(txId), eq(allTasks)))
            .thenReturn(allTasks);

        when(sagaEventRepository.existsById(sagaEvent.getId()))
            .thenReturn(true);

        sagaService.onSagaEvent(sagaEvent);

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).findFinishLogTypeKeyAndIteration(eq(txId), eq("FIRST-PARALEL-TASK"), isNull());
        verify(logRepository).getFinishLogsTypeKeys(eq(txId), eq(allTasks));
        verify(transactionRepository).saveAndFlush(refEq(mockTx(txId, FINISHED)));
        verify(taskExecutor).onFinish(refEq(mockTx(txId, FINISHED)), eq(taskContext));
        verify(sagaEventRepository).existsById(sagaEvent.getId());
        verify(sagaEventRepository).deleteById(sagaEvent.getId());

        noMoreInteraction();
    }

    @Test
    public void resendEventIfNotAllDependsTaskFinished() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();

        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));
        when(logRepository.findFinishLogTypeKeyAndIteration(eq(txId), eq("NEXT-JOIN-TASK"), isNull())).thenReturn(Optional.empty());
        when(logRepository.getFinishLogsTypeKeys(eq(txId), eq(List.of("PARALEL-TASK1", "PARALEL-TASK2"))))
            .thenReturn(List.of("PARALEL-TASK1"));

        sagaService.onSagaEvent(new SagaEvent().setTenantKey("XM")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId));

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).findFinishLogTypeKeyAndIteration(eq(txId), eq("NEXT-JOIN-TASK"), isNull());
        verify(logRepository).getFinishLogsTypeKeys(eq(txId), eq(List.of("PARALEL-TASK1", "PARALEL-TASK2")));
        verify(retryService).retryForWaitDependsTask(refEq(new SagaEvent().setTenantKey("XM")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId), "id"), any(), any());

        noMoreInteraction();
    }

    @Test
    public void successExecuteTaskAndSendNextEvents() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        SagaTaskSpec sagaTaskSpec = sagaTaskSpec();
        Continuation continuation = new Continuation();

        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));
        when(logRepository.findFinishLogTypeKeyAndIteration(eq(txId), eq("NEXT-JOIN-TASK"), isNull())).thenReturn(Optional.empty());
        when(logRepository.getFinishLogsTypeKeys(eq(txId), eq(List.of("PARALEL-TASK1", "PARALEL-TASK2"))))
            .thenReturn(List.of("PARALEL-TASK1", "PARALEL-TASK2"));
        when(sagaEventRepository.saveAndFlush(refEq(inQueueEvent(txId, "SOME-OTHER-TASK", null, "NEXT-JOIN-TASK"), "id")))
            .thenReturn(inQueueEvent(txId, "SOME-OTHER-TASK", "saved-event" + txId, "NEXT-JOIN-TASK"));
        when(sagaEventRepository.findById("saved-event" + txId))
            .thenReturn(Optional.of(inQueueEvent(txId, "NEXT-JOIN-TASK", "saved-event" + txId, null)));
        when(sagaEventRepository.existsById("saved-event" + txId)).thenReturn(true);

        SagaEvent sagaEvent = mockEvent(txId, "NEXT-JOIN-TASK", "saved-event" + txId);
        sagaService.onSagaEvent(sagaEvent);

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).findFinishLogTypeKeyAndIteration(eq(txId), eq("NEXT-JOIN-TASK"), isNull());
        verify(logRepository).getFinishLogsTypeKeys(eq(txId), eq(List.of("PARALEL-TASK1", "PARALEL-TASK2")));
        verify(logRepository).getFinishLogs(eq(txId), eq(List.of("PARALEL-TASK1", "PARALEL-TASK2")));
        verify(logRepository).findLogs(eq(EVENT_START), eq(mockTx(txId, NEW)), eq("NEXT-JOIN-TASK"), isNull());
        verify(logRepository).saveAndFlush(refEq(createLog(txId, "NEXT-JOIN-TASK", EVENT_START)));
        verify(taskExecutor).onCheckWaitCondition(sagaTaskSpec, sagaEvent, mockTx(txId));
        verify(taskExecutor).executeTask(refEq(sagaTaskSpec), refEq(sagaEvent, "id"), refEq(mockTx(txId)), refEq(continuation));
        verify(sagaEventRepository).findById("saved-event" + txId);
        verify(sagaEventRepository).existsById("saved-event" + txId);
        verify(sagaEventRepository).deleteById("saved-event" + txId);
        verify(sagaEventRepository).saveAndFlush(refEq(inQueueEvent(txId, "SOME-OTHER-TASK", null, "NEXT-JOIN-TASK"), "id"));
        verify(eventsManager).sendEvent(refEq(inQueueEvent(txId, "SOME-OTHER-TASK", "saved-event" + txId, "NEXT-JOIN-TASK")));
        verify(logRepository).findLogs(eq(EVENT_END), eq(mockTx(txId, NEW)), eq("NEXT-JOIN-TASK"), isNull());
        verify(logRepository).saveAndFlush(refEq(createLog(txId, "NEXT-JOIN-TASK", EVENT_END)));
        verify(logRepository).getFinishLogsTypeKeys(eq(txId), eq(allTasks));

        noMoreInteraction();
    }

    private SagaEvent mockEvent(String txId, String typeKey, String id) {
        return new SagaEvent().setTenantKey("XM")
            .setId(id)
            .setTypeKey(typeKey)
            .setCreateDate(Instant.now(clock))
            .setTransactionId(txId);
    }

    private SagaEvent inQueueEvent(String txId, String typeKey, String id, String parentTypeKey) {
        return new SagaEvent().setTenantKey("XM")
            .setId(id)
            .setStatus(IN_QUEUE)
            .setTypeKey(typeKey)
            .setParentTypeKey(parentTypeKey)
            .setCreateDate(Instant.now(clock))
            .setTransactionId(txId);
    }

    @Test
    public void successRestoreTaskContextFromDb() {
        String txId = UUID.randomUUID().toString();

        SagaEvent sagaEvent = new SagaEvent();
        sagaEvent.getTaskContext().put("key-1", "value-1");
        sagaEvent.setTransactionId(txId);
        sagaEvent.setTypeKey("SOME-OTHER-TASK");

        when(tenantUtils.getTenantKey())
            .thenReturn("XM");
        when(sagaEventRepository.findById(sagaEvent.getId()))
            .thenReturn(of(sagaEvent));
        when(transactionRepository.findById(txId))
            .thenReturn(of(mockTx(txId, NEW)));

        Map<String, Object> taskContext = new HashMap<>();
        taskContext.put("key-2", "value-2");

        sagaService.continueTask(sagaEvent.getId(), taskContext);

        assertTrue(sagaEvent.getTaskContext().containsKey("key-1"));
        assertTrue(sagaEvent.getTaskContext().containsKey("key-2"));
    }

    private SagaTaskSpec sagaTaskSpec() {
        return new SagaTaskSpec()
            .setBackOff(5).setMaxBackOff(30)
            .setDepends(List.of("PARALEL-TASK1", "PARALEL-TASK2"))
            .setKey("NEXT-JOIN-TASK")
            .setRetryCount(-1L)
            .setRetryPolicy(RETRY)
            .setDependsStrategy(DependsStrategy.ALL_EXECUTED)
            .setNext(List.of("SOME-OTHER-TASK"));
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
        when(logRepository.findFinishLogTypeKeyAndIteration(eq(txId), eq("NEXT-JOIN-TASK"), isNull())).thenReturn(Optional.empty());
        when(logRepository.getFinishLogsTypeKeys(eq(txId), eq(List.of("PARALEL-TASK1", "PARALEL-TASK2"))))
            .thenReturn(List.of("PARALEL-TASK1", "PARALEL-TASK2"));
        when(logRepository.getFinishLogs(eq(txId), eq(List.of("PARALEL-TASK1", "PARALEL-TASK2"))))
            .thenReturn(List.of(new SagaLog().setEventTypeKey("PARALEL-TASK1").setLogType(EVENT_END),
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
        verify(logRepository).findFinishLogTypeKeyAndIteration(eq(txId), eq("NEXT-JOIN-TASK"), isNull());
        verify(logRepository).getFinishLogsTypeKeys(eq(txId), eq(List.of("PARALEL-TASK1", "PARALEL-TASK2")));
        verify(logRepository).getFinishLogs(eq(txId), eq(List.of("PARALEL-TASK1", "PARALEL-TASK2")));
        verify(logRepository).findLogs(eq(EVENT_START), eq(mockTx(txId, NEW)), eq("NEXT-JOIN-TASK"), isNull());
        verify(logRepository).saveAndFlush(refEq(createLog(txId, "NEXT-JOIN-TASK", EVENT_START)));
        verify(taskExecutor).onCheckWaitCondition(sagaTaskSpec, sagaEvent, mockTx(txId));
        verify(taskExecutor).executeTask(refEq(sagaTaskSpec), refEq(sagaEvent), refEq(mockTx(txId)), refEq(continuation));
        verify(retryService).retry(refEq(new SagaEvent().setTenantKey("XM")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId), "id", "error"), any(), any());
        verify(sagaEventRepository).findById("eventId");
        verify(sagaEventRepository).saveAndFlush(refEq(new SagaEvent().setTenantKey("XM")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId)
            .setError(new SagaEventError()
                .setCode(GENERAL_ERROR_CODE)
                .setDescription("TEST EXCEPTION")), "id"));
        noMoreInteraction();
    }

    @Test
    public void failedExecuteTaskWithBusinessException() {

        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        SagaTaskSpec sagaTaskSpec = sagaTaskSpec();
        Continuation continuation = new Continuation();

        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));
        when(logRepository.findFinishLogTypeKeyAndIteration(eq(txId), eq("NEXT-JOIN-TASK"), isNull())).thenReturn(Optional.empty());
        when(logRepository.getFinishLogsTypeKeys(eq(txId), eq(List.of("PARALEL-TASK1", "PARALEL-TASK2"))))
            .thenReturn(List.of("PARALEL-TASK1", "PARALEL-TASK2"));
        when(logRepository.getFinishLogs(eq(txId), eq(List.of("PARALEL-TASK1", "PARALEL-TASK2"))))
            .thenReturn(List.of(new SagaLog().setEventTypeKey("PARALEL-TASK1").setLogType(EVENT_END),
                new SagaLog().setEventTypeKey("PARALEL-TASK2").setLogType(EVENT_END)));
        doThrow(new BusinessException("test.error.code", "TEST EXCEPTION")).when(taskExecutor)
            .executeTask(refEq(sagaTaskSpec), any(), refEq(mockTx(txId)), refEq(continuation));

        SagaEvent sagaEvent = new SagaEvent().setTenantKey("XM")
            .setId("eventId")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId);

        when(sagaEventRepository.findById("eventId")).thenReturn(Optional.of(sagaEvent));

        sagaService.onSagaEvent(sagaEvent);

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).findFinishLogTypeKeyAndIteration(eq(txId), eq("NEXT-JOIN-TASK"), isNull());
        verify(logRepository).getFinishLogsTypeKeys(eq(txId), eq(List.of("PARALEL-TASK1", "PARALEL-TASK2")));
        verify(logRepository).getFinishLogs(eq(txId), eq(List.of("PARALEL-TASK1", "PARALEL-TASK2")));
        verify(logRepository).findLogs(eq(EVENT_START), eq(mockTx(txId, NEW)), eq("NEXT-JOIN-TASK"), isNull());
        verify(logRepository).saveAndFlush(refEq(createLog(txId, "NEXT-JOIN-TASK", EVENT_START)));
        verify(taskExecutor).onCheckWaitCondition(sagaTaskSpec, sagaEvent, mockTx(txId));
        verify(taskExecutor).executeTask(refEq(sagaTaskSpec), refEq(sagaEvent), refEq(mockTx(txId)), refEq(continuation));
        verify(retryService).retry(refEq(new SagaEvent().setTenantKey("XM")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId), "id", "error"), any(), any());
        verify(sagaEventRepository).findById("eventId");
        verify(sagaEventRepository).saveAndFlush(refEq(new SagaEvent().setTenantKey("XM")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId)
            .setError(new SagaEventError()
                .setCode("test.error.code")
                .setDescription("TEST EXCEPTION")), "id"));
        noMoreInteraction();
    }

    @Test
    public void taskWaitForCondition() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        SagaTaskSpec sagaTaskSpec = sagaTaskSpec();

        SagaEvent sagaEvent = new SagaEvent().setTenantKey("XM")
            .setId("eventId")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId);

        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));
        when(logRepository.findFinishLogTypeKeyAndIteration(eq(txId), eq("NEXT-JOIN-TASK"), isNull())).thenReturn(Optional.empty());
        when(logRepository.getFinishLogsTypeKeys(eq(txId), eq(List.of("PARALEL-TASK1", "PARALEL-TASK2"))))
            .thenReturn(List.of("PARALEL-TASK1", "PARALEL-TASK2"));
        when(taskExecutor.onCheckWaitCondition(sagaTaskSpec, sagaEvent, mockTx(txId))).thenReturn(false);

        sagaService.onSagaEvent(sagaEvent);

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).findFinishLogTypeKeyAndIteration(eq(txId), eq("NEXT-JOIN-TASK"), isNull());
        verify(logRepository).getFinishLogsTypeKeys(eq(txId), eq(List.of("PARALEL-TASK1", "PARALEL-TASK2")));
        verify(taskExecutor).onCheckWaitCondition(sagaTaskSpec, sagaEvent, mockTx(txId));
        verify(retryService).retryForTaskWaitCondition(refEq(new SagaEvent().setTenantKey("XM")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId), "id"), any(), any());

        noMoreInteraction();
    }

    @Test
    public void successExecuteTaskAndFinishTransaction() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        SagaTaskSpec sagaTaskSpec = sagaTaskSpec().setKey("SOME-OTHER-TASK").setDepends(emptyList()).setNext(emptyList());
        Continuation continuation = new Continuation();

        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));
        when(logRepository.findFinishLogTypeKeyAndIteration(eq(txId), eq("SOME-OTHER-TASK"), isNull())).thenReturn(Optional.empty());
        when(logRepository.getFinishLogsTypeKeys(eq(txId), eq(allTasks)))
            .thenReturn(allTasks);

        Map<String, Object> taskContext = Map.of("EXECUTION-RESULT", "SUCCESS");
        SagaEvent sagaEvent = new SagaEvent().setId("eventId").setTenantKey("XM")
            .setTypeKey("SOME-OTHER-TASK")
            .setTransactionId(txId);

        when(sagaEventRepository.findById("eventId")).thenReturn(Optional.of(sagaEvent));
        when(sagaEventRepository.existsById("eventId")).thenReturn(true);
        when(taskExecutor.executeTask(eq(sagaTaskSpec), eq(sagaEvent),
            refEq(mockTx(txId, NEW)), eq(continuation))).thenReturn(taskContext);

        sagaService.onSagaEvent(sagaEvent);

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).findFinishLogTypeKeyAndIteration(eq(txId), eq("SOME-OTHER-TASK"), isNull());
        verify(logRepository).findLogs(eq(EVENT_START), refEq(mockTx(txId, NEW), "sagaTransactionState"), eq("SOME-OTHER-TASK"), isNull());
        verify(logRepository).saveAndFlush(refEq(createLog(txId, "SOME-OTHER-TASK", EVENT_START), "sagaTransaction"));
        verify(taskExecutor).executeTask(refEq(sagaTaskSpec), refEq(sagaEvent), refEq(mockTx(txId), "sagaTransactionState"), refEq(continuation));
        verify(taskExecutor).onCheckWaitCondition(sagaTaskSpec, sagaEvent, mockTx(txId, FINISHED));
        verify(logRepository).findLogs(eq(EVENT_END), refEq(mockTx(txId, NEW), "sagaTransactionState"), eq("SOME-OTHER-TASK"), isNull());
        verify(logRepository).saveAndFlush(refEq(createLog(txId, "SOME-OTHER-TASK", EVENT_END), "sagaTransaction"));
        verify(logRepository).getFinishLogsTypeKeys(eq(txId), eq(allTasks));
        verify(transactionRepository).saveAndFlush(refEq(mockTx(txId, FINISHED)));
        verify(taskExecutor).onFinish(refEq(mockTx(txId, FINISHED)), eq(taskContext));
        verify(sagaEventRepository).findById("eventId");
        verify(sagaEventRepository).existsById("eventId");
        verify(sagaEventRepository).deleteById("eventId");

        noMoreInteraction();
    }

    @Test
    public void setConfigurationFromTransaction() throws IOException {
        specService.onRefresh("/config/tenants/XM/activation/activation-spec.yml", loadFile("spec/activation-spec-task-configuration.yml"));
        when(tenantUtils.getTenantKey()).thenReturn("XM");

        SagaTransactionSpec transactionSpecWithConfiguration = specService.getTransactionSpec(fromTypeKey("TEST-TYPE-KEY"));
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
        SagaTaskSpec taskSpec = specService.getTransactionSpec(transaction).getTask(eventTypeKey);

        when(transactionRepository.findById(txId)).thenReturn(of(transaction));
        when(logRepository.findFinishLogTypeKeyAndIteration(eq(txId), eq(eventTypeKey), isNull())).thenReturn(Optional.empty());
        when(taskExecutor.executeTask(taskSpec, sagaEvent, transaction, continuation))
            .then(it -> {
                latch.await(2, SECONDS);
                return emptyMap();
            });
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
        verify(logRepository, times(2)).findFinishLogTypeKeyAndIteration(eq(txId), eq(eventTypeKey), isNull());
        verify(sagaEventRepository, times(2)).findById(sagaEvent.getId());
        verify(taskExecutor, times(2)).onCheckWaitCondition(taskSpec, sagaEvent, transaction);
        verify(taskExecutor).executeTask(taskSpec, sagaEvent, transaction, continuation);
        verify(logRepository).findLogs(eq(EVENT_START), refEq(transaction), eq(sagaEvent.getTypeKey()), isNull());
        verify(logRepository).saveAndFlush(refEq(createLog(txId, sagaEvent.getTypeKey(), EVENT_START), "sagaTransaction"));
        verify(logRepository).findLogs(eq(EVENT_END), refEq(transaction), eq(sagaEvent.getTypeKey()), isNull());
        verify(logRepository).saveAndFlush(refEq(createLog(txId, sagaEvent.getTypeKey(), EVENT_END), "sagaTransaction"));
        verify(sagaEventRepository).existsById(sagaEvent.getId());
        verify(sagaEventRepository).deleteById(sagaEvent.getId());
        verify(logRepository).getFinishLogsTypeKeys(eq(txId), eq(List.of(eventTypeKey, "SUSPEND-TASK")));

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
        SagaTaskSpec taskSpec = specService.getTransactionSpec(transaction).getTask(eventTypeKey);

        when(transactionRepository.findById(txId)).thenReturn(of(transaction));
        when(logRepository.findFinishLogTypeKeyAndIteration(eq(txId), eq(eventTypeKey), isNull())).thenReturn(Optional.empty());
        when(taskExecutor.executeTask(taskSpec, sagaEvent, transaction, continuation)).thenReturn(emptyMap());
        when(sagaEventRepository.findById(sagaEvent.getId())).thenReturn(Optional.of(sagaEvent));

        sagaService.onSagaEvent(sagaEvent);
        sagaService.onSagaEvent(sagaEvent);

        verify(transactionRepository, times(2)).findById(txId);
        verify(logRepository, times(2)).findFinishLogTypeKeyAndIteration(eq(txId), eq(eventTypeKey), isNull());
        verify(sagaEventRepository, times(2)).findById(sagaEvent.getId());
        verify(taskExecutor, times(2)).onCheckWaitCondition(taskSpec, sagaEvent, transaction);
        verify(taskExecutor).executeTask(taskSpec, sagaEvent, transaction, continuation);
        verify(logRepository).findLogs(eq(EVENT_START), refEq(transaction), eq(sagaEvent.getTypeKey()), isNull());
        verify(logRepository).saveAndFlush(refEq(createLog(txId, sagaEvent.getTypeKey(), EVENT_START), "sagaTransaction"));
        verify(sagaEventRepository).saveAndFlush(mockEvent(txId, eventTypeKey, taskId).setStatus(SUSPENDED));

        noMoreInteraction();
    }

    @Test
    @SneakyThrows
    public void testTransactionDeduplication() {
        String key = UUID.randomUUID().toString();
        String txTypeKey = "TASK-AND-TASK-WITH-SUSPEND-TX";
        SagaTransaction tx1 = mockTx(UUID.randomUUID().toString(), NEW).setTypeKey(txTypeKey).setKey(key);
        SagaTransaction tx2 = mockTx(UUID.randomUUID().toString(), NEW).setTypeKey(txTypeKey).setKey(key);
        int countTransactionWillStarted = 1;

        when(tenantUtils.getTenantKey()).thenReturn("XM");

        MutableBoolean isSaved = new MutableBoolean(false);
        when(transactionRepository.findByKey(key)).then(mock -> {
            if (isSaved.booleanValue()) {
                return of(tx1);
            } else {
                return empty();
            }
        });
        when(transactionRepository.saveAndFlush(tx1)).then(mock -> {
            isSaved.setValue(true);
            return tx1;
        });

        sagaService.createNewSaga(tx1);
        sagaService.createNewSaga(tx2);

        verify(transactionRepository, times(2)).findByKey(eq(key));
        verify(transactionRepository, times(countTransactionWillStarted)).saveAndFlush(refEq(tx1));
        verify(sagaEventRepository, times(2)).saveAndFlush(any());
        verify(eventsManager, times(2)).sendEvent(any());

        noMoreInteraction();
    }

    @Test
    @SneakyThrows
    public void testTransactionCreationWhereKeyIsNull() {
        String txTypeKey = "TASK-AND-TASK-WITH-SUSPEND-TX";
        SagaTransaction tx1 = mockTx(UUID.randomUUID().toString(), NEW).setTypeKey(txTypeKey);
        SagaTransaction tx2 = mockTx(UUID.randomUUID().toString(), NEW).setTypeKey(txTypeKey);

        when(tenantUtils.getTenantKey()).thenReturn("XM");

        when(transactionRepository.findByKey(tx1.getKey())).thenReturn(empty());
        when(transactionRepository.findByKey(tx2.getKey())).thenReturn(empty());
        when(transactionRepository.saveAndFlush(tx1)).thenReturn(tx1);
        when(transactionRepository.saveAndFlush(tx2)).thenReturn(tx2);

        sagaService.createNewSaga(tx1);
        sagaService.createNewSaga(tx2);

        verify(transactionRepository).findByKey(eq(tx1.getKey()));
        verify(transactionRepository).findByKey(eq(tx2.getKey()));
        verify(transactionRepository).saveAndFlush(refEq(tx1));
        verify(transactionRepository).saveAndFlush(refEq(tx2));
        verify(sagaEventRepository, times(4)).saveAndFlush(any());
        verify(eventsManager, times(4)).sendEvent(any());

        noMoreInteraction();
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

