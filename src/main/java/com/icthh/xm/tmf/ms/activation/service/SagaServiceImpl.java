package com.icthh.xm.tmf.ms.activation.service;

import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventType.ON_RETRY;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventType.SUSPENDED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_END;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_START;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.CANCELED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.FINISHED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy.RETRY;
import static com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy.ROLLBACK;
import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import com.icthh.xm.commons.exceptions.EntityNotFoundException;
import com.icthh.xm.commons.logging.aop.IgnoreLogginAspect;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaLogRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Without transaction! Important!
 * Each write to database is atomic operation and should be commited.
 * For example start log should not rollback if execute task failed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaServiceImpl implements SagaService {

    private final SagaLogRepository logRepository;
    private final SagaTransactionRepository transactionRepository;
    private final SagaSpecService specService;
    private final EventsSender eventsManager;
    private final TenantUtils tenantUtils;
    private final SagaTaskExecutor taskExecutor;
    private final RetryService retryService;
    private final SagaEventRepository sagaEventRepository;
    private Clock clock = Clock.systemUTC();

    @Override
    public SagaTransaction createNewSaga(SagaTransaction sagaTransaction) {
        specService.getTransactionSpec(sagaTransaction.getTypeKey());
        sagaTransaction.setId(null);
        sagaTransaction.setSagaTransactionState(NEW);
        sagaTransaction.setCreateDate(Instant.now(clock));
        SagaTransaction saved = transactionRepository.save(sagaTransaction);
        log.info("Saga transaction created {}", sagaTransaction);
        generateFirstEvents(saved);
        return saved;
    }

    @Override
    @IgnoreLogginAspect
    public void onSagaEvent(SagaEvent sagaEvent) {

        Context context = initContext(sagaEvent);
        List<BiFunction<SagaEvent, Context, Boolean>> preconditions = asList(this::isTransactionExists,
                                                                             this::isTransactionInCorrectState,
                                                                             this::isCurrentTaskNotFinished,
                                                                             this::isAllDependsTaskFinished);

        for (val precondition : preconditions) {
            if (!precondition.apply(sagaEvent, context)) {
                return;
            }
        }

        SagaTransaction transaction = context.getTransaction();
        SagaTransactionSpec transactionSpec = context.getTransactionSpec();
        SagaTaskSpec taskSpec = context.getTaskSpec();

        writeLog(sagaEvent, transaction, EVENT_START);

        try {
            StopWatch stopWatch = StopWatch.createStarted();
            log.info("Start execute task by event {} transaction {}", sagaEvent, transaction);
            Continuation continuation = new Continuation();
            Map<String, Object> taskContext = taskExecutor.executeTask(taskSpec, sagaEvent, transaction, continuation);
            if (TRUE.equals(taskSpec.getIsSuspendable()) && !continuation.isContinuationFlag()) {
                log.info("Task by event {} suspended. Time: {}ms", sagaEvent, transaction, stopWatch.getTime());
                sagaEventRepository.save(sagaEvent.setStatus(SUSPENDED));
            } else {
                log.info("Finish execute task by event {}. Time: {}ms", sagaEvent, transaction, stopWatch.getTime());
                continuation(sagaEvent, transaction, transactionSpec, taskSpec, taskContext);
            }
        } catch (Exception e) {
            log.error("Error execute task.", e);
            failHandler(transaction, sagaEvent, taskSpec);
        }
    }

    @Override
    public void continueTask(String taskId, Map<String, Object> taskContext) {
        SagaEvent sagaEvent = sagaEventRepository.findById(taskId)
                                                 .orElseThrow(
                                                     () -> entityNotFound("Task with id " + taskId + " not found"));
        Context context = initContext(sagaEvent);
        SagaTransaction transaction = context.getTransaction();
        SagaTransactionSpec transactionSpec = context.getTransactionSpec();
        SagaTaskSpec taskSpec = context.getTaskSpec();
        continuation(sagaEvent, transaction, transactionSpec, taskSpec, taskContext);
        sagaEventRepository.delete(sagaEvent);
    }

    private void continuation(SagaEvent sagaEvent, SagaTransaction transaction, SagaTransactionSpec transactionSpec,
                              SagaTaskSpec taskSpec, Map<String, Object> taskContext) {
        List<SagaTaskSpec> tasks = taskSpec.getNext().stream().map(transactionSpec::getTask).collect(toList());
        generateEvents(transaction.getId(), tasks, taskContext);
        writeLog(sagaEvent, transaction, EVENT_END);
        updateTransactionStatus(transaction, transactionSpec);
    }

    private Context initContext(SagaEvent sagaEvent) {
        return transactionRepository.findById(sagaEvent.getTransactionId())
                                    .map(tx -> transactionToContext(sagaEvent, tx))
                                    .orElse(null);
    }

    private boolean isTransactionExists(SagaEvent sagaEvent, Context context) {
        if (context == null) {
            log.error("Transaction with id {} not found.", sagaEvent.getTransactionId());
            eventsManager.resendEvent(sagaEvent);
            return false;
        }
        return true;
    }

    private boolean isTransactionInCorrectState(SagaEvent sagaEvent, Context context) {
        SagaTransaction transaction = context.getTransaction();
        if (NEW != transaction.getSagaTransactionState()) {
            log.warn("Transaction {} in incorrect state. Event {} skipped.", transaction, sagaEvent);
            return false;
        }
        return true;
    }

    private boolean isAllDependsTaskFinished(SagaEvent sagaEvent, Context context) {
        SagaTaskSpec taskSpec = context.getTaskSpec();
        String txId = context.getTxId();
        Collection<String> notFinishedTasks = getNotFinishedTasks(txId, taskSpec.getDepends());
        if (!notFinishedTasks.isEmpty()) {
            log.warn("Task will not execute. Depends tasks {} not finished. Transaction id {}.", notFinishedTasks,
                     txId);
            retry(sagaEvent, context);
            return false;
        }
        return true;
    }

    private boolean isCurrentTaskNotFinished(SagaEvent sagaEvent, Context context) {
        SagaTransaction transaction = context.getTransaction();
        SagaTransactionSpec transactionSpec = context.getTransactionSpec();
        if (isTaskFinished(sagaEvent, context.getTxId())) {
            log.warn("Task is already finished. Event {} skipped. Transaction {}.", transaction, sagaEvent);
            updateTransactionStatus(transaction, transactionSpec);
            return false;
        }
        return true;
    }

    private Context transactionToContext(SagaEvent sagaEvent, SagaTransaction transaction) {
        SagaTransactionSpec transactionSpec = specService.getTransactionSpec(transaction.getTypeKey());
        SagaTaskSpec taskSpec = transactionSpec.getTask(sagaEvent.getTypeKey());
        return new Context(transaction, transactionSpec, taskSpec);
    }

    private boolean isTaskFinished(SagaEvent sagaEvent, String txId) {
        return getNotFinishedTasks(txId, singletonList(sagaEvent.getTypeKey())).isEmpty();
    }

    @Override
    public void cancelSagaTransaction(String sagaTxKey) {
        SagaTransaction sagaTransaction = getById(sagaTxKey);
        sagaTransaction.setSagaTransactionState(CANCELED);
        transactionRepository.save(sagaTransaction);
    }

    @Override
    public Page<SagaTransaction> getAllNewTransaction(Pageable pageable) {
        return transactionRepository.findAllBySagaTransactionState(NEW, pageable);
    }

    @Override
    public void retrySagaEvent(String txid, String eventId) {
        SagaEvent sagaEvent = sagaEventRepository.findById(eventId)
                                                 .orElseThrow(
                                                     () -> entityNotFound("Event by id " + eventId + " not found"));
        retryService.doResend(sagaEvent);
    }

    @Override
    public List<SagaEvent> getEventsByTransaction(String txId) {
        return sagaEventRepository.findByTransactionId(txId);
    }

    @Override
    public List<SagaLog> getLogsByTransaction(String txId) {
        return logRepository.findBySagaTransactionId(txId);
    }

    @Override
    public Page<SagaTransaction> getAllTransaction(Pageable pageable) {
        return transactionRepository.findAll(pageable);
    }

    private void generateFirstEvents(SagaTransaction sagaTransaction) {
        SagaTransactionSpec spec = specService.getTransactionSpec(sagaTransaction.getTypeKey());
        generateEvents(sagaTransaction.getId(), spec.getFirstTasks(), emptyMap());
    }

    private void generateEvents(String sagaTransactionId, List<SagaTaskSpec> sagaTaskSpecs,
                                Map<String, Object> taskContext) {

        String tenantKey = tenantUtils.getTenantKey();
        sagaTaskSpecs.stream()
                     .map(task -> new SagaEvent().setTypeKey(task.getKey())
                                                 .setTenantKey(tenantKey)
                                                 .setCreateDate(Instant.now(clock))
                                                 .setTaskContext(taskContext)
                                                 .setTransactionId(sagaTransactionId))
                     .forEach(eventsManager::sendEvent);
    }

    private void updateTransactionStatus(SagaTransaction transaction, SagaTransactionSpec transactionSpec) {
        List<String> txTasks = transactionSpec.getTasks().stream().map(SagaTaskSpec::getKey).collect(toList());
        Collection<String> notFinishedTasks = getNotFinishedTasks(transaction.getId(), txTasks);
        if (notFinishedTasks.isEmpty()) {
            transactionRepository.save(transaction.setSagaTransactionState(FINISHED));
            taskExecutor.onFinish(transaction);
        }
    }

    private void retry(SagaEvent sagaEvent, Context context) {
        retryService.retry(sagaEvent, context.getTaskSpec());
    }

    private void failHandler(SagaTransaction transaction, SagaEvent sagaEvent, SagaTaskSpec taskSpec) {
        if (taskSpec.getRetryPolicy() == RETRY) {
            log.info("Using retry strategy.");
            retryService.retry(sagaEvent, taskSpec);
        } else if (taskSpec.getRetryPolicy() == ROLLBACK) {
            log.info("Using rollback strategy.");
            // TODO implement rollback strategy
            throw new NotImplementedException("Rollback strategy unimplemented now.");
        }
    }

    private Collection<String> getNotFinishedTasks(String sagaTxId, List<String> taskKeys) {
        if (taskKeys.isEmpty()) {
            return emptyList();
        }

        List<SagaLog> finished = logRepository.getFinishLogs(sagaTxId, taskKeys);
        log.debug("Finished tasks {} by keys {}", finished, taskKeys);
        Set<String> depends = new HashSet<>(taskKeys);
        finished.forEach(log -> depends.remove(log.getEventTypeKey()));
        return depends;
    }

    private void writeLog(SagaEvent sagaEvent, SagaTransaction transaction, SagaLogType eventType) {
        SagaLog sagaLog = new SagaLog().setLogType(eventType)
                                       .setCreateDate(Instant.now(clock))
                                       .setEventTypeKey(sagaEvent.getTypeKey())
                                       .setSagaTransaction(transaction);
        if (logRepository.findLogs(eventType, transaction, sagaEvent.getTypeKey()).isEmpty()) {
            logRepository.save(sagaLog);
            log.info("Write saga log {}", sagaLog);
        } else {
            log.warn("Saga log already exists {}", sagaLog);
        }
    }

    private SagaTransaction getById(String sagaTxKey) {
        return transactionRepository.findById(sagaTxKey)
                                    .orElseThrow(() -> entityNotFound("Transaction by id " + sagaTxKey + " not found"));
    }

    private EntityNotFoundException entityNotFound(String message) {
        return new EntityNotFoundException(message);
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    @Data
    @AllArgsConstructor
    private static class Context {
        private SagaTransaction transaction;
        private SagaTransactionSpec transactionSpec;
        private SagaTaskSpec taskSpec;

        public String getTxId() {
            return transaction.getId();
        }
    }

}
