package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.commons.exceptions.BusinessException;
import com.icthh.xm.commons.exceptions.EntityNotFoundException;
import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.commons.lep.spring.LepService;
import com.icthh.xm.commons.logging.aop.IgnoreLogginAspect;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaEventError;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaLogRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.resolver.TaskTypeKeyResolver;
import com.icthh.xm.tmf.ms.activation.resolver.TransactionTypeKeyResolver;
import com.icthh.xm.tmf.ms.activation.service.SagaSpecService.InvalidSagaSpecificationException;
import com.icthh.xm.tmf.ms.activation.utils.JsonPathUtil;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import com.icthh.xm.tmf.ms.activation.utils.TransactionUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import static com.icthh.xm.tmf.ms.activation.config.Constants.GENERAL_ERROR_CODE;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.INVALID_SPECIFICATION;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.IN_QUEUE;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.SUSPENDED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.WAIT_DEPENDS_TASK;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_END;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_START;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.REJECTED_BY_CONDITION;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.CANCELED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static com.icthh.xm.tmf.ms.activation.domain.spec.DependsStrategy.ALL_EXECUTED_OR_REJECTED;
import static com.icthh.xm.tmf.ms.activation.domain.spec.DependsStrategy.AT_LEAST_ONE_EXECUTED;
import static com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy.RETRY;
import static com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy.ROLLBACK;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Objects.isNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;

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

    public static final String LOOP_RESULT_CONTEXTS = "contexts";
    private final SagaLogRepository logRepository;
    private final SagaTransactionRepository transactionRepository;
    private final SagaSpecService specService;
    private final EventsSender eventsManager;
    private final TenantUtils tenantUtils;
    private final SagaTaskExecutor taskExecutor;
    private final RetryService retryService;
    private final SagaEventRepository sagaEventRepository;
    private final TransactionStatusStrategy updateTransactionStrategy;
    private Clock clock = Clock.systemUTC();
    private final Map<String, Boolean> executingTask = new ConcurrentHashMap<>();

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

        SagaTransactionSpec transactionSpec = specService.getTransactionSpec(sagaTransaction);// check type key is exists in specification
        sagaTransaction.setId(null);
        sagaTransaction.setSagaTransactionState(NEW);
        sagaTransaction.setCreateDate(Instant.now(clock));
        sagaTransaction.setSpecificationVersion(transactionSpec.getVersion());
        SagaTransaction saved = transactionRepository.save(sagaTransaction);
        log.info("Saga transaction created {} with context {}", sagaTransaction, sagaTransaction.getContext());
        generateFirstEvents(saved, transactionSpec);
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

        if (isCurrentTaskFinished(sagaEvent, context)) {
            deleteSagaEvent(sagaEvent);
            return;
        }

        if (!isAllConditionalsValid(context, sagaEvent,
            this::isAllDependsTaskFinished,
            this::isCurrentTaskNotWaitForCondition,
            this::isTaskNotSuspended)) {
            return;
        }

        /*
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

        writeLog(sagaEvent, transaction, EVENT_START, taskSpec, sagaEvent.getTaskContext(), sagaEvent.getIteration());

        try {
            if (needRejectByDependedTasks(transaction.getId(), taskSpec)) {
                log.info("Task by event {} rejected by depended tasks. Transaction: {}", sagaEvent, transaction);
                rejectTask(transaction.getId(), sagaEvent.getTypeKey(), sagaEvent.getTypeKey(), context);
                updateTransactionStrategy.updateTransactionStatus(transaction, transactionSpec, sagaEvent.getTaskContext());
                return;
            }

            if (!TRUE.equals(self.checkCondition(taskSpec, sagaEvent, transaction))) {
                log.info("Task by event {} rejected by condition. Transaction: {}", sagaEvent, transaction);
                rejectTask(transaction.getId(), sagaEvent.getTypeKey(), sagaEvent.getTypeKey(), context);
                updateTransactionStrategy.updateTransactionStatus(transaction, transactionSpec, sagaEvent.getTaskContext());
                return;
            }

            if (taskSpec.isIterable() && sagaEvent.getIteration() == null) {
                int countOfIteration = generateIterableEvents(transaction.getId(), sagaEvent.getTypeKey(), taskSpec, sagaEvent.getTaskContext());
                sagaEvent.setIterationsCount(countOfIteration);
                if (countOfIteration <= 0) {
                    self.finishIterableTask(sagaEvent, transaction, transactionSpec, taskSpec, Map.of());
                }
                return;
            }

            StopWatch stopWatch = StopWatch.createStarted();
            log.info("Start execute task by event {} transaction {}", sagaEvent, transaction);
            Continuation continuation = new Continuation(taskSpec.getIsSuspendable());
            Set<String> nextTasks = new HashSet<>(taskSpec.getNext());

            Map<String, Object> taskContext = taskExecutor.executeTask(taskSpec, sagaEvent, transaction, continuation);

            nextTasks.removeAll(taskSpec.getNext());
            nextTasks.forEach(task -> rejectTask(transaction.getId(), taskSpec.getKey(), task, context));

            runChildTransaction(taskSpec, sagaEvent, taskContext, continuation);
            if (!continuation.isContinuationFlag()) {
                log.info("Task by event {} suspended. Transaction: {}. Time: {}ms", sagaEvent, transaction, stopWatch.getTime());
                sagaEventRepository.save(sagaEvent.setStatus(SUSPENDED));
            } else {
                log.info("Finish execute task by event {}. Transaction: {}. Time: {}ms", sagaEvent, transaction, stopWatch.getTime());
                continuation(sagaEvent, transaction, transactionSpec, taskSpec, taskContext);
                deleteSagaEvent(sagaEvent);
            }
        } catch (Exception e) {
            log.error("Error execute task.", e);
            failHandler(transaction, sagaEvent, taskSpec, e);
        }
    }

    private void runChildTransaction(SagaTaskSpec taskSpec, SagaEvent sagaEvent, Map<String, Object> taskContext, Continuation continuation) {
        if (isNoneBlank(taskSpec.getChildTransactionKey())) {
            continuation.suspend();
            createNewSaga(new SagaTransaction()
                .setKey(sagaEvent.getId())
                .setTypeKey(taskSpec.getChildTransactionKey())
                .setContext(taskContext)
                .setParentTxId(sagaEvent.getTransactionId())
                .setParentEventId(sagaEvent.getId()));
        }
    }

    @LogicExtensionPoint(value = "Condition", resolver = TaskTypeKeyResolver.class)
    public Boolean checkCondition(SagaTaskSpec task, SagaEvent sagaEvent, SagaTransaction sagaTransaction) {
        return true;
    }

    private boolean needRejectByDependedTasks(String sagaTxId, SagaTaskSpec taskSpec) {
        List<String> dependsTasks = taskSpec.getDepends();
        if (dependsTasks.isEmpty()) {
            return false;
        }
        if (ALL_EXECUTED_OR_REJECTED.equals(taskSpec.getDependsStrategy())) {
            return false;
        }

        List<SagaLog> finished = logRepository.getFinishLogs(sagaTxId, dependsTasks);
        log.debug("Finished dependent tasks {} by keys {}", finished, dependsTasks);
        long countRejected = finished.stream().filter(it -> REJECTED_BY_CONDITION.equals(it.getLogType())).count();
        if (countRejected == 0) {
            return false;
        }
        if (dependsTasks.size() == countRejected) {
            return true;
        }
        if (AT_LEAST_ONE_EXECUTED.equals(taskSpec.getDependsStrategy())) {
            return false;
        }
        return true; // here countRejected > 0 and ALL_EXECUTED strategy or null
    }

    private void rejectTask(String transactionId, final String currentTaskKey, String rejectKey, Context context) {
        if (canTaskBeExecutedLater(currentTaskKey, rejectKey, context) || isTaskFinished(rejectKey, context.getTxId())) {
            return;
        }
        markAsRejectedByCondition(rejectKey, context);
        Optional<SagaEvent> eventToDelete = sagaEventRepository.findByTransactionIdAndTypeKey(transactionId, rejectKey);
        eventToDelete.ifPresent(this::deleteSagaEvent);
        SagaTaskSpec currentTask = context.getTransactionSpec().getTask(rejectKey);
        currentTask.getNext().forEach(nextTask -> rejectTask(transactionId, rejectKey, nextTask, context));
        resendEventsForDependentTasks(context.getTransactionSpec(), currentTask, transactionId);
    }

    private boolean canTaskBeExecutedLater(final String currentTaskKey, String rejectKey, Context context) {
        // rejected task in own execution context have to be rejected
        if (currentTaskKey.equals(rejectKey)) {
            return false;
        }

        // task can be already in progress or in queue by another branch, or just in another branch that will be executed
        Optional<SagaEvent> event = sagaEventRepository.findByTransactionIdAndTypeKey(context.transaction.getId(), rejectKey);
        event.ifPresent(e -> log.info("Found event {}", e));
        boolean isInProgress = event.isPresent();
        return isInProgress || isPresentInOtherNotFinishedTasks(currentTaskKey, rejectKey, context);
    }

    private boolean isPresentInOtherNotFinishedTasks(final String currentTaskKey, String rejectKey, Context context) {
        List<SagaTaskSpec> tasksWithoutCurrent = context.getTransactionSpec().getTasks().stream()
            .filter(task -> !task.getKey().equals(currentTaskKey)).collect(toList());
        List<String> keys = tasksWithoutCurrent.stream().map(SagaTaskSpec::getKey).collect(toList());
        Set<String> notFinishedTasks = getNotFinishedTasks(context.transaction.getId(), keys);
        log.info("Not finished tasks: {}", notFinishedTasks);
        tasksWithoutCurrent = tasksWithoutCurrent.stream().filter(task -> notFinishedTasks.contains(task.getKey())).collect(toList());
        return tasksWithoutCurrent.stream().anyMatch(task -> task.getNext().contains(rejectKey));
    }

    private void markAsRejectedByCondition(String taskKey, Context context) {
        final SagaEvent sagaEvent = new SagaEvent().setTypeKey(taskKey);
        writeLog(sagaEvent, context.getTransaction(), REJECTED_BY_CONDITION, context.getTaskSpec(), Map.of(), sagaEvent.getIteration());
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

        self.internalContinueTask(context.getTransaction(), sagaEvent, context.getTaskSpec(), context);
    }

    @LogicExtensionPoint(value = "ContinueTask", resolver = TaskTypeKeyResolver.class)
    public void internalContinueTask(SagaTransaction sagaTransaction, SagaEvent sagaEvent, SagaTaskSpec task, Context context) {
        continuation(sagaEvent,
            sagaTransaction,
            context.getTransactionSpec(),
            task,
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
        if (taskSpec.isIterable()) {
            self.finishIterableTask(sagaEvent, transaction, transactionSpec, taskSpec, taskContext);
        } else {
            self.finishTask(sagaEvent, transaction, transactionSpec, taskSpec, taskContext, sagaEvent.getIteration());
        }
    }

    private void generateNextEvents(SagaEvent sagaEvent, SagaTransaction transaction, SagaTransactionSpec transactionSpec,
                                    SagaTaskSpec taskSpec, Map<String, Object> taskContext) {
        var tasks = taskSpec.getNext().stream().map(transactionSpec::getTask).collect(toList());
        generateEvents(transaction.getId(), sagaEvent.getTypeKey(), tasks, taskContext);
    }

    @Transactional
    public void finishTask(SagaEvent sagaEvent, SagaTransaction transaction, SagaTransactionSpec transactionSpec,
                            SagaTaskSpec taskSpec, Map<String, Object> taskContext, Integer iteration) {
        generateNextEvents(sagaEvent, transaction, transactionSpec, taskSpec, taskContext);
        writeLog(sagaEvent, transaction, EVENT_END, taskSpec, taskContext, iteration);
        resendEventsForDependentTasks(transactionSpec, taskSpec, sagaEvent.getTransactionId()); // after write end log
        updateTransactionStrategy.updateTransactionStatus(transaction, transactionSpec, taskContext);
    }

    @Transactional
    public void finishIterableTask(SagaEvent sagaEvent, SagaTransaction transaction, SagaTransactionSpec transactionSpec,
                                    SagaTaskSpec taskSpec, Map<String, Object> taskContext) {
        if (sagaEvent.getIteration() != null) {
            writeLog(sagaEvent, transaction, EVENT_END, taskSpec, taskContext, sagaEvent.getIteration());
        }

        Integer iterationCount = firstNonNull(sagaEvent.getIterationsCount(), 0);
        Integer countEndLogs = logRepository.countByIterableLogs(transaction.getId(), sagaEvent.getTypeKey());
        log.info("Finished {} from {} tasks of {}", sagaEvent.getTypeKey(), countEndLogs, sagaEvent.getIterationsCount());
        if (countEndLogs >= iterationCount) {
            Integer iteration = firstNonNull(sagaEvent.getIteration(), -1);
            SagaEvent iterableEvent = createIterableEvent(
                sagaEvent.getTransactionId(),
                sagaEvent.getParentTypeKey(),
                taskSpec,
                sagaEvent.getTaskContext(),
                iteration + 1,
                iterationCount
            );
            if (taskExecutor.continueIterableLoopCondition(taskSpec, iterableEvent, transaction)) {
                saveAndSendEvent(iterableEvent);
            } else {
                finishLoop(sagaEvent, transaction, transactionSpec, taskSpec);
            }
        }
    }

    private void finishLoop(SagaEvent sagaEvent, SagaTransaction transaction,
                            SagaTransactionSpec transactionSpec, SagaTaskSpec taskSpec) {
        Map<String, Object> resultTaskContext = new HashMap<>();
        if (TRUE.equals(taskSpec.getSaveTaskContext())) {
            List<Map<String, Object>> taskContexts = logRepository.getResultTaskContexts(sagaEvent.getTypeKey(), transaction.getId());
            resultTaskContext.put(LOOP_RESULT_CONTEXTS, taskContexts);
        }
        SagaEvent event = sagaEventRepository.findByTransactionIdAndTypeKeyAndIterationIsNull(transaction.getId(), sagaEvent.getTypeKey());
        self.finishTask(event, transaction, transactionSpec, taskSpec, resultTaskContext, null);
        deleteSagaEvent(event);
    }

    private void resendEventsForDependentTasks(SagaTransactionSpec transactionSpec, SagaTaskSpec taskSpec, String transactionId) {
        if (TRUE.equals(transactionSpec.getCheckDependsEventually())) {
            List<String> dependentTaskKeys = transactionSpec.findDependentTasks(taskSpec.getKey());
            List<SagaTaskSpec> dependentTasks = transactionSpec.getTasks().stream()
                .filter(task -> dependentTaskKeys.contains(task.getKey())).collect(toList());
            List<String> logsThatNeedDependentTasks = dependentTasks.stream()
                .map(SagaTaskSpec::getDepends)
                .flatMap(Collection::stream)
                .distinct().collect(toList());

            if (CollectionUtils.isEmpty(logsThatNeedDependentTasks)) {
                return;
            }

            Set<String> finishedTasks = new HashSet<>(logRepository.getFinishLogsTypeKeys(transactionId, logsThatNeedDependentTasks));

            List<String> dependentTaskReadyToResend = dependentTasks.stream()
                .filter(it -> finishedTasks.containsAll(it.getDepends()))
                .map(SagaTaskSpec::getKey)
                .collect(toList());

            if (CollectionUtils.isEmpty(dependentTaskReadyToResend)) {
                return;
            }

            List<SagaEvent> dependentEvents = sagaEventRepository.findByTransactionIdAndTypeKeyIn(transactionId, dependentTaskReadyToResend);
            dependentEvents.stream()
                .filter(not(SagaEvent::isInQueue))
                .peek(SagaEvent::markAsInQueue)
                .map(sagaEventRepository::save)
                .forEach(eventsManager::sendEvent);
        }
    }

    @LogicExtensionPoint(value = "OnResendEvent")
    public void resendEvent(SagaEvent sagaEvent) {
        eventsManager.resendEvent(sagaEvent);
    }

    private DraftContext initContext(SagaEvent sagaEvent) {
        var transaction = transactionRepository.findById(sagaEvent.getTransactionId());
        var transactionSpec = transaction.flatMap(specService::findTransactionSpec);
        var taskSpec = transactionSpec.map(it -> it.getTask(sagaEvent.getTypeKey()));

        return new DraftContext(transaction, transactionSpec, taskSpec);
    }

    @SafeVarargs
    private <T> boolean isAllConditionalsValid(T context, SagaEvent sagaEvent,
                                               BiFunction<SagaEvent, T, Boolean> ...conditionals) {
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
        SagaTransactionSpec transactionSpec = context.getTransactionSpec();
        SagaTransaction sagaTransaction = context.getTransaction();
        String txId = context.getTxId();
        Collection<String> notFinishedTasks = getNotFinishedTasks(txId, taskSpec.getDepends());
        if (!notFinishedTasks.isEmpty()) {
            log.warn("Task will not execute. Depends tasks {} not finished. Transaction id {}.", notFinishedTasks,
                txId);
            if (TRUE.equals(transactionSpec.getCheckDependsEventually())) {
                log.debug("Depends tasks will be checked eventually after finish \"depends\" tasks. Transaction id {}.", txId);
                sagaEvent.setStatus(WAIT_DEPENDS_TASK);
                sagaEventRepository.save(sagaEvent);
            } else {
                retryService.retryForWaitDependsTask(sagaEvent, sagaTransaction, context.getTaskSpec());
            }
            return false;
        }
        return true;
    }

    private boolean isTaskNotSuspended(SagaEvent sagaEvent, Context context) {
        var isTaskSuspended = sagaEventRepository.findById(sagaEvent.getId()).map(SagaEvent::isSuspended).orElse(FALSE);
        return !isTaskSuspended;
    }

    private boolean isCurrentTaskFinished(SagaEvent sagaEvent, Context context) {
        SagaTransaction transaction = context.getTransaction();
        SagaTransactionSpec transactionSpec = context.getTransactionSpec();
        if (isTaskFinished(sagaEvent.getTypeKey(), context.getTxId(), sagaEvent.getIteration())) {
            log.warn("Task is already finished. Event {} skipped. Transaction {}.", sagaEvent, transaction);
            updateTransactionStrategy.updateTransactionStatus(transaction, transactionSpec, sagaEvent.getTaskContext());
            return true;
        }
        return false;
    }

    private boolean isTaskFinished(String eventTypeKey, String txId, Integer iteration) {
        return logRepository.findFinishLogTypeKeyAndIteration(txId, eventTypeKey, iteration).isPresent();
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
    public Optional<SagaEvent> getEventById(String eventId) {
        return sagaEventRepository.findById(eventId);
    }

    @Override
    public List<SagaLog> getLogsByTransaction(String txId) {
        return logRepository.findBySagaTransactionId(txId);
    }

    @Override
    public SagaLog getLogsByTransactionEventTypeAndLogType(String txId, String eventType, SagaLogType logType) {
        return logRepository.findBySagaTransactionIdAndEventTypeKeyAndLogTypeAndIterationIsNull(txId, eventType, logType);
    }

    @Override
    public Page<SagaTransaction> getAllTransaction(Pageable pageable) {
        return transactionRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    @LogicExtensionPoint("FindTransactionById")
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

    private void eventNotFound(String eventId) {
        log.warn("Event with id {} not found. No context will be updated.", eventId);
    }

    private void generateFirstEvents(SagaTransaction sagaTransaction, SagaTransactionSpec spec) {
        generateEvents(sagaTransaction.getId(), null, spec.getFirstTasks(), emptyMap());
    }

    private void generateEvents(String sagaTransactionId, String parentTypeKey, List<SagaTaskSpec> sagaTaskSpecs,
                                Map<String, Object> taskContext) {
        sagaTaskSpecs.stream()
            .map(task -> createEvent(sagaTransactionId, parentTypeKey, task, taskContext))
            .forEach(this::saveAndSendEvent);
    }

    private int generateIterableEvents(String sagaTransactionId, String parentTypeKey, SagaTaskSpec task,
                                       Map<String, Object> taskContext) {
        String iterablePath = task.getIterableJsonPath();
        Object iterable = JsonPathUtil.getByPath(taskContext, iterablePath, task);
        Integer countOfIteration = calculateCountOfIteration(iterable, task);
        if (countOfIteration <= 0) {
            log.warn("Iterable by path {} is {}. Task {} will not be created.", iterablePath, iterable, task.getKey());
        }
        range(0, countOfIteration).forEach(iteration ->
                generateIterableEvent(sagaTransactionId, parentTypeKey, task, taskContext, iteration, countOfIteration)
            );
        return countOfIteration;
    }

    private void generateIterableEvent(String sagaTransactionId, String parentTypeKey, SagaTaskSpec task,
                                       Map<String, Object> taskContext, Integer iteration, Integer countOfIteration) {
        SagaEvent sagaEvent = createIterableEvent(sagaTransactionId, parentTypeKey, task, taskContext, iteration, countOfIteration);
        saveAndSendEvent(sagaEvent);
    }

    private void saveAndSendEvent(SagaEvent sagaEvent) {
        sagaEvent.markAsInQueue();
        sagaEvent = sagaEventRepository.save(sagaEvent);
        sendEventOnCommitted(sagaEvent);
    }

    private SagaEvent createIterableEvent(String sagaTransactionId, String parentTypeKey, SagaTaskSpec task,
                                          Map<String, Object> taskContext, Integer iteration, Integer countOfIteration) {
        return createEvent(sagaTransactionId, parentTypeKey, task, taskContext)
            .setIteration(iteration)
            .setIterationsCount(countOfIteration);
    }


    private SagaEvent createEvent(String sagaTransactionId, String parentTypeKey, SagaTaskSpec task, Map<String, Object> taskContext) {
        String tenantKey = tenantUtils.getTenantKey();
        return new SagaEvent().setTypeKey(task.getKey())
            .setParentTypeKey(parentTypeKey)
            .setTenantKey(tenantKey)
            .setCreateDate(Instant.now(clock))
            .setTaskContext(taskContext)
            .setTransactionId(sagaTransactionId);
    }

    private Integer calculateCountOfIteration(Object iterable, SagaTaskSpec task) {
        if (isNull(iterable)) {
            Integer defaultIterationsCount = task.getDefaultIterationsCount();
            return defaultIterationsCount == null ? 0 : defaultIterationsCount;
        } else if (iterable instanceof Number) {
            return ((Number) iterable).intValue();
        } else if (iterable instanceof Collection) {
            return ((Collection<?>) iterable).size();
        } else {
            log.error("Iterable should be Number or Collection. But it is {}", iterable.getClass());
            throw new IllegalArgumentException("Iterable should be Number or Collection");
        }
    }

    private void failHandler(SagaTransaction transaction, SagaEvent sagaEvent, SagaTaskSpec taskSpec, Exception e) {
        exceptionFailHandler(sagaEvent, e);

        if (taskSpec.getRetryPolicy() == RETRY) {
            log.info("Using retry strategy.");
            retryService.retry(sagaEvent, transaction, taskSpec);
        } else if (taskSpec.getRetryPolicy() == ROLLBACK) {
            log.info("Using rollback strategy.");
            // TODO implement rollback strategy
            throw new NotImplementedException("Rollback strategy unimplemented now.");
        }
    }

    private void exceptionFailHandler(SagaEvent sagaEvent, Exception e) {
        SagaEventError error = new SagaEventError().setDescription(e.getMessage());
        if (e instanceof BusinessException) {
            BusinessException be = (BusinessException) e;
            error.setCode(be.getCode());
        } else {
            error.setCode(GENERAL_ERROR_CODE);
        }
        sagaEvent.setError(error);
        sagaEventRepository.save(sagaEvent);
    }

    private Set<String> getNotFinishedTasks(String sagaTxId, List<String> taskKeys) {
        if (taskKeys.isEmpty()) {
            return emptySet();
        }

        Set<String> finished = new HashSet<>(logRepository.getFinishLogsTypeKeys(sagaTxId, taskKeys));
        log.debug("Finished tasks {} by keys {}", finished, taskKeys);
        Set<String> depends = new HashSet<>(taskKeys);
        depends.removeAll(finished);
        return depends;
    }

    private void writeLog(SagaEvent sagaEvent,
                          SagaTransaction transaction,
                          SagaLogType eventType,
                          SagaTaskSpec taskSpec,
                          Map<String, Object> taskContext,
                          Integer iteration) {
        SagaLog sagaLog = new SagaLog().setLogType(eventType)
            .setCreateDate(Instant.now(clock))
            .setEventTypeKey(sagaEvent.getTypeKey())
            .setIteration(iteration)
            .setIterationsCount(sagaEvent.getIterationsCount())
            .setSagaTransaction(transaction);

        if (TRUE.equals(taskSpec.getSaveTaskContext())) {
            sagaLog.setTaskContext(taskContext);
        }

        List<SagaLog> logs = logRepository.findLogs(eventType, transaction, sagaEvent.getTypeKey(), iteration);
        if (logs.isEmpty()) {
            logRepository.save(sagaLog);
            log.info("Write saga log {}", sagaLog);
        } else {
            log.warn("Saga logs already exist: {}", logs);
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

    private void sendEventOnCommitted(SagaEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionUtils.executeIfCommited(
                () -> eventsManager.sendEvent(event),
                () -> log.warn("Event was not sent due to the parent task's failed completion. Event: {}", event)
            );
        } else {
            log.warn("Send event without transaction synchronization. ID: {}", event.getId());
            eventsManager.sendEvent(event);
        }
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
