package com.icthh.xm.tmf.ms.activation.service;

import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_END;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_START;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.CANCELED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.FINISHED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState;
import com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaLogRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class SagaServiceTest {

    public static final String TEST_TYPE_KEY = "TEST-SAGA-TYPE-KEY";

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

    private List<String> allTasks = asList("FIRST-PARALEL-TASK", "PARALEL-TASK1", "PARALEL-TASK2", "NEXT-JOIN-TASK",
        "SECOND-PARALEL-TASK", "SOME-OTHER-TASK");

    @Before
    public void before() throws IOException {
        specService = new SagaSpecService(tenantUtils);
        sagaService = new SagaServiceImpl(logRepository, transactionRepository, specService, eventsManager,
            tenantUtils, taskExecutor, retryService, sagaEventRepository);
        specService.onRefresh("/config/tenants/XM/activation/activation-spec.yml", loadFile("spec/activation-spec.yml"));
    }

    public static String loadFile(String path) throws IOException {
        return IOUtils.toString(new ClassPathResource(path).getInputStream(), UTF_8);
    }

    @Test
    public void testGenerageFirstEventOnCreateSagaTx() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        when(transactionRepository.save(refEq(mockTx()))).thenReturn(mockTx().setId(txId));

        sagaService.createNewSaga(new SagaTransaction().setTypeKey(TEST_TYPE_KEY));

        verify(transactionRepository).save(refEq(mockTx()));
        verify(eventsManager).sendEvent(refEq(new SagaEvent().setTenantKey("XM")
            .setTypeKey("FIRST-PARALEL-TASK")
            .setTransactionId(txId), "id"));
        verify(eventsManager).sendEvent(refEq(new SagaEvent().setTenantKey("XM")
            .setTypeKey("SECOND-PARALEL-TASK")
            .setTransactionId(txId), "id"));

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
        verify(retryService).retry(refEq(new SagaEvent().setTenantKey("XM")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId), "id"), any());

        noMoreInteraction();
    }

    @Test
    public void successExecuteTaskAndSendNextEvents() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        SagaTaskSpec sagaTaskSpec = sagaTaskSpec();

        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));
        when(logRepository.getFinishLogs(eq(txId), eq(asList("NEXT-JOIN-TASK")))).thenReturn(emptyList());
        when(logRepository.getFinishLogs(eq(txId), eq(asList("PARALEL-TASK1", "PARALEL-TASK2"))))
            .thenReturn(asList(new SagaLog().setEventTypeKey("PARALEL-TASK1").setLogType(EVENT_END),
                new SagaLog().setEventTypeKey("PARALEL-TASK2").setLogType(EVENT_END)));
        //(taskExecutor.executeTask(refEq(sagaTaskSpec), refEq(mockTx(txId)))));

        SagaEvent sagaEvent = new SagaEvent().setTenantKey("XM")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId);
        sagaService.onSagaEvent(sagaEvent);

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).getFinishLogs(eq(txId), eq(asList("NEXT-JOIN-TASK")));
        verify(logRepository).getFinishLogs(eq(txId), eq(asList("PARALEL-TASK1", "PARALEL-TASK2")));
        verify(logRepository).findLogs(eq(EVENT_START), eq(mockTx(txId, NEW)), eq("NEXT-JOIN-TASK"));
        verify(logRepository).save(refEq(createLog(txId, "NEXT-JOIN-TASK", EVENT_START)));
        verify(taskExecutor).executeTask(refEq(sagaTaskSpec), refEq(sagaEvent), refEq(mockTx(txId)));
        verify(eventsManager).sendEvent(refEq(new SagaEvent().setTenantKey("XM")
            .setTypeKey("SOME-OTHER-TASK")
            .setTransactionId(txId), "id"));

        verify(logRepository).findLogs(eq(EVENT_END), eq(mockTx(txId, NEW)), eq("NEXT-JOIN-TASK"));
        verify(logRepository).save(refEq(createLog(txId, "NEXT-JOIN-TASK", EVENT_END)));
        verify(logRepository).getFinishLogs(eq(txId), eq(allTasks));

        noMoreInteraction();
    }

    private SagaTaskSpec sagaTaskSpec() {
        return new SagaTaskSpec()
                .setBackOff(5).setMaxBackOff(30)
                .setDepends(asList("PARALEL-TASK1", "PARALEL-TASK2"))
                .setKey("NEXT-JOIN-TASK")
                .setRetryCount(-1L)
                .setRetryPolicy(RetryPolicy.RETRY)
                .setNext(asList("SOME-OTHER-TASK"));
    }

    private SagaLog createLog(String txId, String eventTypeKey, SagaLogType eventStart) {
        return new SagaLog().setEventTypeKey(eventTypeKey)
            .setLogType(eventStart).setSagaTransaction(mockTx(txId));
    }

    @Test
    public void failedExecuteTaskAndResendEvent() {

        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        SagaTaskSpec sagaTaskSpec = sagaTaskSpec();

        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));
        when(logRepository.getFinishLogs(eq(txId), eq(asList("NEXT-JOIN-TASK")))).thenReturn(emptyList());
        when(logRepository.getFinishLogs(eq(txId), eq(asList("PARALEL-TASK1", "PARALEL-TASK2"))))
            .thenReturn(asList(new SagaLog().setEventTypeKey("PARALEL-TASK1").setLogType(EVENT_END),
                new SagaLog().setEventTypeKey("PARALEL-TASK2").setLogType(EVENT_END)));
        doThrow(new RuntimeException("TEST EXCEPTION")).when(taskExecutor)
            .executeTask(refEq(sagaTaskSpec), any(), refEq(mockTx(txId)));

        SagaEvent sagaEvent = new SagaEvent().setTenantKey("XM")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId);
        sagaService.onSagaEvent(sagaEvent);

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).getFinishLogs(eq(txId), eq(asList("NEXT-JOIN-TASK")));
        verify(logRepository).getFinishLogs(eq(txId), eq(asList("PARALEL-TASK1", "PARALEL-TASK2")));
        verify(logRepository).findLogs(eq(EVENT_START), eq(mockTx(txId, NEW)), eq("NEXT-JOIN-TASK"));
        verify(logRepository).save(refEq(createLog(txId, "NEXT-JOIN-TASK", EVENT_START)));
        verify(taskExecutor).executeTask(refEq(sagaTaskSpec), refEq(sagaEvent), refEq(mockTx(txId)));
        verify(retryService).retry(refEq(new SagaEvent().setTenantKey("XM")
            .setTypeKey("NEXT-JOIN-TASK")
            .setTransactionId(txId), "id"), any());

        noMoreInteraction();
    }

    @Test
    public void successExecuteTaskAndFinishTransaction() {
        when(tenantUtils.getTenantKey()).thenReturn("XM");
        String txId = UUID.randomUUID().toString();
        SagaTaskSpec sagaTaskSpec = sagaTaskSpec().setKey("SOME-OTHER-TASK").setDepends(emptyList()).setNext(emptyList());

        when(transactionRepository.findById(txId)).thenReturn(of(mockTx(txId, NEW)));
        when(logRepository.getFinishLogs(eq(txId), eq(asList("SOME-OTHER-TASK")))).thenReturn(emptyList());
        when(logRepository.getFinishLogs(eq(txId), eq(allTasks)))
            .thenReturn(allTasks.stream().map(key -> new SagaLog().setEventTypeKey(key).setLogType(EVENT_END)).collect(toList()));

        SagaEvent sagaEvent = new SagaEvent().setTenantKey("XM")
            .setTypeKey("SOME-OTHER-TASK")
            .setTransactionId(txId);
        sagaService.onSagaEvent(sagaEvent);

        verify(transactionRepository).findById(eq(txId));
        verify(logRepository).getFinishLogs(eq(txId), eq(asList("SOME-OTHER-TASK")));
        verify(logRepository).findLogs(eq(EVENT_START), refEq(mockTx(txId, NEW), "sagaTransactionState"), eq("SOME-OTHER-TASK"));
        verify(logRepository).save(refEq(createLog(txId, "SOME-OTHER-TASK", EVENT_START), "sagaTransaction"));
        verify(taskExecutor).executeTask(refEq(sagaTaskSpec), refEq(sagaEvent), refEq(mockTx(txId), "sagaTransactionState"));
        verify(logRepository).findLogs(eq(EVENT_END), refEq(mockTx(txId, NEW), "sagaTransactionState"), eq("SOME-OTHER-TASK"));
        verify(logRepository).save(refEq(createLog(txId, "SOME-OTHER-TASK", EVENT_END), "sagaTransaction"));
        verify(logRepository).getFinishLogs(eq(txId), eq(allTasks));
        verify(transactionRepository).save(refEq(mockTx(txId, FINISHED)));
        verify(taskExecutor).onFinish(refEq(mockTx(txId, FINISHED)));

        noMoreInteraction();
    }

    private SagaTransaction mockTx() {
        return new SagaTransaction()
            .setTypeKey(TEST_TYPE_KEY)
            .setSagaTransactionState(NEW);
    }

    private SagaTransaction mockTx(String id) {
        return mockTx().setId(id);
    }

    private SagaTransaction mockTx(SagaTransactionState state) {
        return mockTx().setSagaTransactionState(state);
    }

    private SagaTransaction mockTx(String id, SagaTransactionState state) {
        return mockTx().setSagaTransactionState(state).setId(id);
    }

    public void noMoreInteraction() {
        Mockito.verifyNoMoreInteractions(logRepository);
        Mockito.verifyNoMoreInteractions(transactionRepository);
        Mockito.verifyNoMoreInteractions(eventsManager);
        Mockito.verifyNoMoreInteractions(taskExecutor);
    }


}
