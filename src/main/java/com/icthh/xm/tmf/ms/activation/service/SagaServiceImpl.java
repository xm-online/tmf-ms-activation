package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.commons.exceptions.EntityNotFoundException;
import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.commons.lep.spring.LepService;
import com.icthh.xm.commons.logging.aop.IgnoreLogginAspect;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaLogRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.resolver.TransactionTypeKeyResolver;
import com.icthh.xm.tmf.ms.activation.service.SagaSpecService.InvalidSagaSpecificationException;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.INVALID_SPECIFICATION;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.IN_QUEUE;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.SUSPENDED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_END;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_START;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.REJECTED_BY_CONDITION;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.CANCELED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.FINISHED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy.RETRY;
import static com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy.ROLLBACK;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Without transaction! Important!
 * Each write to database is atomic operation and should be commited.
 * For example start log should not rollback if execute task failed.
 */
@Slf4j
@LepService(group = "service.saga")
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
    private Map<String, Boolean> executingTask = new ConcurrentHashMap<>();

    @Setter(onMethod = @__(@Autowired))
    private SagaServiceImpl self;

    @LogicExtensionPoint(value = "CreateNewSaga", resolver = TransactionTypeKeyResolver.class)
    @Override
    public SagaTransaction createNewSaga(SagaTransaction sagaTransaction) {
        if (isEmpty(sagaTransaction.getKey())) {
            sagaTransaction.setKey(UUID.randomUUID().toString());
        }

        Optional<SagaTransaction> existsTransaction = transactionRepository.findByKey(sagaTransaction.getKey());
        if (existsTransaction.isPresent()) {
            log.warn("Saga transaction with key {} already created", sagaTransaction.getKey());
            return existsTransaction.get();
        }

        specService.getTransactionSpec(sagaTransaction.getTypeKey()); // check type key is exists in specification
        sagaTransaction.setId(null);
        sagaTransaction.setSagaTransactionState(NEW);
        sagaTransaction.setCreateDate(Instant.now(clock));
        SagaTransaction saved = transactionRepository.save(sagaTransaction);
        log.info("Saga transaction created {} with context {}", sagaTransaction, sagaTransaction.getContext());
        generateFirstEvents(saved);
        return saved;
    }

    @Override
    @IgnoreLogginAspect
    public void onSagaEvent(SagaEvent sagaEvent) {
        try {
            handleSagaEvent(sagaEvent);
        } catch (Exception e) {
            log.error("Error handle saga event", e);
            // For avoid stop partition, in some crazy situation, send to end of the queue.
            self.resendEvent(sagaEvent);
        }
    }

    private void handleSagaEvent(SagaEvent sagaEvent) {
        DraftContext draftContext = initContext(sagaEvent);
        if (!isAllConditionalsValid(draftContext, sagaEvent,
            this::isTransactionExists,
            this::isTransactionInCorrectState,
            this::isValidSpec)) {
            return;
        }

        Context context = draftContext.createContext();
        if (!isAllConditionalsValid(context, sagaEvent,
            this::isCurrentTaskNotFinished,
            this::isAllDependsTaskFinished,
            this::isCurrentTaskNotWaitForCondition,
            this::isTaskNotSuspended)) {
            return;
        }

        /**
         This check avoid duplication executing event.
         As example after resend events marked as in queue {@link #resendEventsByStateInQueue()}
         This check valid in multi node environment, because transaction id used as partition key in kafka.
         Case with cluster rebalance known and acceptable.
         */
        if (TRUE.equals(executingTask.putIfAbsent(sagaEvent.getId(), true))) {
            log.warn("Message already executing: {}", sagaEvent);
            return;
        }
        try {
            doTask(sagaEvent, context);
        } finally {
            executingTask.remove(sagaEvent.getId());
        }
    }

    private void doTask(SagaEvent sagaEvent, Context context) {
        SagaTransaction transaction = context.getTransaction();
        SagaTransactionSpec transactionSpec = context.getTransactionSpec();
        SagaTaskSpec taskSpec = context.getTaskSpec();

        writeLog(sagaEvent, transaction, EVENT_START);

        try {
            StopWatch stopWatch = StopWatch.createStarted();
            log.info("Start execute task by event {} transaction {}", sagaEvent, transaction);
            Continuation continuation = new Continuation();
            Set<String> nextTasks = new HashSet<>(taskSpec.getNext());
            Map<String, Object> taskContext = taskExecutor.executeTask(taskSpec, sagaEvent, transaction, continuation);
            nextTasks.removeAll(taskSpec.getNext());
            nextTasks.forEach(task -> rejectTask(taskSpec.getKey(), task, context));
            if (TRUE.equals(taskSpec.getIsSuspendable()) && !continuation.isContinuationFlag()) {
                log.info("Task by event {} suspended. Transaction: {}. Time: {}ms", sagaEvent, transaction, stopWatch.getTime());
                sagaEventRepository.save(sagaEvent.setStatus(SUSPENDED));
            } else {
                log.info("Finish execute task by event {}. Transaction: {}. Time: {}ms", sagaEvent, transaction, stopWatch.getTime());
                continuation(sagaEvent, transaction, transactionSpec, taskSpec, taskContext);
                deleteSagaEvent(sagaEvent);
            }
        } catch (Exception e) {
            log.error("Error execute task.", e);
            failHandler(transaction, sagaEvent, taskSpec);
        }
    }

    private void rejectTask(final String currentTaskKey, String rejectKey, Context context) {
        if (isPresentInOtherNotFinishedTasks(currentTaskKey, rejectKey, context) ||
            isTaskFinished(rejectKey, context.getTxId())) {
            return;
        }
        markAsRejectedByCondition(rejectKey, context);
        SagaTaskSpec currentTask = context.getTransactionSpec().getTask(rejectKey);
        currentTask.getNext().forEach(nextTask -> rejectTask(rejectKey, nextTask, context));
    }

    private boolean isPresentInOtherNotFinishedTasks(final String currentTaskKey, String rejectKey, Context context) {
        List<SagaTaskSpec> tasksWithoutCurrent = context.getTransactionSpec().getTasks().stream()
            .filter(task -> !task.getKey().equals(currentTaskKey)).collect(toList());
        return tasksWithoutCurrent.stream()
            .filter(task -> !isTaskFinished(task.getKey(), context.getTxId()))
            .anyMatch(task -> task.getNext().contains(rejectKey));
    }

    private void markAsRejectedByCondition(String taskKey, Context context) {
        writeLog(new SagaEvent().setTypeKey(taskKey), context.getTransaction(), REJECTED_BY_CONDITION);
    }

    @LogicExtensionPoint("ContinueTask")
    @Override
    public void continueTask(String taskId, Map<String, Object> taskContext) {
        SagaEvent sagaEvent = sagaEventRepository.findById(taskId)
            .orElseThrow(
                () -> entityNotFound("Task with id " + taskId + " not found"));
        DraftContext draftContext = initContext(sagaEvent);

        if (!draftContext.isValidSpec()) {
            log.error("Can not continue task, specification is invalid. Context: {}", draftContext);
            throw new InvalidSagaSpecificationException("Can not continue task, specification is invalid");
        }

        Context context = draftContext.createContext();
        sagaEvent.getTaskContext().putAll(taskContext);

        continuation(sagaEvent,
            context.getTransaction(),
            context.getTransactionSpec(),
            context.getTaskSpec(),
            sagaEvent.getTaskContext());

        deleteSagaEvent(sagaEvent);
    }

    private boolean isValidSpec(SagaEvent sagaEvent, DraftContext context) {
        if (!context.isValidSpec()) {
            log.warn("Specification for event {} not found or invalid.", sagaEvent);
            sagaEventRepository.save(sagaEvent.setStatus(INVALID_SPECIFICATION));
            return false;
        }
        return true;
    }

    private void deleteSagaEvent(SagaEvent sagaEvent) {
        if (sagaEventRepository.existsById(sagaEvent.getId())) {
            log.info("Delete saga event by id {}", sagaEvent.getId());
            sagaEventRepository.deleteById(sagaEvent.getId());
        } else {
            log.warn("Saga event with id {} not present in db", sagaEvent.getId());
        }
    }

    private void continuation(SagaEvent sagaEvent, SagaTransaction transaction, SagaTransactionSpec transactionSpec,
                              SagaTaskSpec taskSpec, Map<String, Object> taskContext) {
        List<SagaTaskSpec> tasks = taskSpec.getNext().stream().map(transactionSpec::getTask).collect(toList());
        generateEvents(transaction.getId(), tasks, taskContext);
        writeLog(sagaEvent, transaction, EVENT_END);
        updateTransactionStatus(transaction, transactionSpec);
    }

    @LogicExtensionPoint(value = "OnResendEvent")
    public void resendEvent(SagaEvent sagaEvent) {
        eventsManager.resendEvent(sagaEvent);
    }

    private DraftContext initContext(SagaEvent sagaEvent) {
        var transaction = transactionRepository.findById(sagaEvent.getTransactionId());
        var transactionSpec = transaction.flatMap(it -> specService.findTransactionSpec(it.getTypeKey()));
        var taskSpec = transactionSpec.map(it -> it.getTask(sagaEvent.getTypeKey()));

        return new DraftContext(transaction, transactionSpec, taskSpec);
    }

    @SafeVarargs
    private <T> boolean isAllConditionalsValid(T context, SagaEvent sagaEvent,
                                               BiFunction<SagaEvent, T, Boolean>... conditionals) {
        for (var condition : conditionals) {
            if (!condition.apply(sagaEvent, context)) {
                return false;
            }
        }
        return true;
    }

    private boolean isTransactionExists(SagaEvent sagaEvent, DraftContext context) {
        if (context.getTransaction().isEmpty()) {
            log.error("Transaction with id {} not found.", sagaEvent.getTransactionId());
            self.resendEvent(sagaEvent);
            return false;
        }
        return true;
    }

    private boolean isTransactionInCorrectState(SagaEvent sagaEvent, DraftContext context) {
        boolean isNew = context.getTransaction()
            .map(SagaTransaction::getSagaTransactionState)
            .map(NEW::equals).orElse(false);
        if (!isNew) {
            log.warn("Transaction in context {} in incorrect state. Event {} skipped.", context, sagaEvent);
            return false;
        }
        return true;
    }

    private boolean isCurrentTaskNotWaitForCondition(SagaEvent sagaEvent, Context context) {
        SagaTaskSpec taskSpec = context.getTaskSpec();
        SagaTransaction transaction = context.getTransaction();
        String txId = context.getTxId();

        try {
            boolean taskExecutionAllowed = taskExecutor.onCheckWaitCondition(taskSpec, sagaEvent, transaction);
            if (!taskExecutionAllowed) {
                log.info("Task will not executed. Wait condition not happened yet. Transaction id {}.", txId);
                retryService.retryForTaskWaitCondition(sagaEvent, transaction, context.getTaskSpec());
                return false;
            }
        } catch (Throwable t) {
            log.error("Task will not executed. Error during condition check. Transaction id {}.", txId);
            retryService.retry(sagaEvent, transaction, context.getTaskSpec());
            return false;
        }
        return true;
    }

    private boolean isAllDependsTaskFinished(SagaEvent sagaEvent, Context context) {
        SagaTaskSpec taskSpec = context.getTaskSpec();
        SagaTransaction sagaTransaction = context.getTransaction();
        String txId = context.getTxId();
        Collection<String> notFinishedTasks = getNotFinishedTasks(txId, taskSpec.getDepends());
        if (!notFinishedTasks.isEmpty()) {
            log.warn("Task will not execute. Depends tasks {} not finished. Transaction id {}.", notFinishedTasks,
                txId);
            retryService.retryForWaitDependsTask(sagaEvent, sagaTransaction, context.getTaskSpec());
            return false;
        }
        return true;
    }

    private boolean isTaskNotSuspended(SagaEvent sagaEvent, Context context) {
        var isTaskSuspended = sagaEventRepository.findById(sagaEvent.getId()).map(SagaEvent::isSuspended).orElse(FALSE);
        return !isTaskSuspended;
    }

    private boolean isCurrentTaskNotFinished(SagaEvent sagaEvent, Context context) {
        SagaTransaction transaction = context.getTransaction();
        SagaTransactionSpec transactionSpec = context.getTransactionSpec();
        if (isTaskFinished(sagaEvent.getTypeKey(), context.getTxId())) {
            log.warn("Task is already finished. Event {} skipped. Transaction {}.", transaction, sagaEvent);
            updateTransactionStatus(transaction, transactionSpec);
            return false;
        }
        return true;
    }

    private boolean isTaskFinished(String eventTypeKey, String txId) {
        return getNotFinishedTasks(txId, singletonList(eventTypeKey)).isEmpty();
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
    public SagaEvent getEventById(String eventId) {
        return sagaEventRepository.findById(eventId)
            .orElseThrow(
                () -> entityNotFound("Event by id " + eventId + " not found"));
    }

    @Override
    public List<SagaLog> getLogsByTransaction(String txId) {
        return logRepository.findBySagaTransactionId(txId);
    }

    @Override
    public Page<SagaTransaction> getAllTransaction(Pageable pageable) {
        return transactionRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<SagaTransaction> findTransactionById(String id) {
        return transactionRepository.findOneById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resendEventsByStateInQueue() {
        sagaEventRepository.findByStatus(IN_QUEUE).forEach(retryService::doResendInQueueEvent);
    }

    @Override
    public SagaTransaction getByKey(String key) {
        return transactionRepository.findByKey(key).orElseThrow(
            () -> entityNotFound("Transaction with key " + key + " not found"));
    }

    @LogicExtensionPoint("UpdateTaskContext")
    @Override
    @Transactional
    public void updateEventContext(String eventId, Map<String, Object> context) {
        sagaEventRepository.findById(eventId)
            .map(it -> it.setTaskContext(context))
            .ifPresentOrElse(sagaEventRepository::save, () -> eventNotFound(eventId));
    }

    @LogicExtensionPoint("UpdateTransactionContext")
    @Override
    @Transactional
    public void updateTransactionContext(String txId, Map<String, Object> context) {
        transactionRepository.findById(txId)
            .map(it -> it.setContext(context))
            .ifPresentOrElse(transactionRepository::save,
                () -> entityNotFound("Transaction with id " + txId + " not found"));
    }

    @Override
    public void changeTransactionState(String txId, SagaTransactionState state) {
        self.changeTransactionStateInner(txId, state);
    }

    public void changeTransactionStateInner(String txId, SagaTransactionState state) {
        log.info("State of transaction:{} will be changed to: {}", txId, state);
        transactionRepository.findById(txId)
            .map(it -> it.setSagaTransactionState(state))
            .ifPresentOrElse(transactionRepository::save,
                () -> entityNotFound("Transaction with id " + txId + " not found"));
    }

    private void eventNotFound(String eventId) {
        log.warn("Event with id {} not found. No context will be updated.", eventId);
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
            .peek(SagaEvent::markAsInQueue)
            .map(sagaEventRepository::save)
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

    private void failHandler(SagaTransaction transaction, SagaEvent sagaEvent, SagaTaskSpec taskSpec) {
        if (taskSpec.getRetryPolicy() == RETRY) {
            log.info("Using retry strategy.");
            retryService.retry(sagaEvent, transaction, taskSpec);
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

    @Data
    @AllArgsConstructor
    private static class DraftContext {
        private Optional<SagaTransaction> transaction;
        private Optional<SagaTransactionSpec> transactionSpec;
        private Optional<SagaTaskSpec> taskSpec;

        public boolean isValidSpec() {
            return transactionSpec.isPresent() && taskSpec.isPresent();
        }

        public Context createContext() {
            return new Context(transaction.get(), transactionSpec.get(), taskSpec.get());
        }
    }

}
