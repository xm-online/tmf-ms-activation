package com.icthh.xm.tmf.ms.activation.service;

import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_END;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_START;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.CANCELED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.FINISHED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy.RETRY;
import static com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy.ROLLBACK;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.springframework.data.jpa.domain.Specification.where;

import com.icthh.xm.commons.exceptions.EntityNotFoundException;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsManager;
import com.icthh.xm.tmf.ms.activation.repository.SagaLogRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import javax.persistence.criteria.Predicate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

@Slf4j
@Service
@RequiredArgsConstructor
// Without transaction! Important!
public class SagaServiceImpl implements SagaService {

    private final SagaLogRepository logRepository;
    private final SagaTransactionRepository transactionRepository;
    private final SagaSpecService specService;
    private final EventsManager eventsManager;
    private final TenantUtils tenantUtils;
    private final TaskExecutor taskExecutor;

    @Override
    public SagaTransaction createNewSaga(SagaTransaction sagaTransaction) {
        sagaTransaction.setId(null);
        sagaTransaction.setSagaTransactionState(NEW);
        SagaTransaction saved = transactionRepository.save(sagaTransaction);
        generateFirstEvents(sagaTransaction);
        return saved;
    }

    @Override
    public void onSagaEvent(SagaEvent sagaEvent) {
        String txId = sagaEvent.getTransactionId();

        Context context = initContext(sagaEvent);
        List<BiFunction<SagaEvent, Context, Boolean>> preconditions = asList(
            this::isTransactionExists,
            this::isTransactionInCorrectState,
            this::isAllDependsTaskFinished,
            this::isCurrentTaskNotFinished
        );

        for (val precondition: preconditions) {
            if (!precondition.apply(sagaEvent, context)) {
                return;
            }
        }

        SagaTransaction transaction = context.getTransaction();
        SagaTransactionSpec transactionSpec = context.getTransactionSpec();
        SagaTaskSpec taskSpec = context.getTaskSpec();
        writeLog(sagaEvent, transaction, EVENT_START);

        execute(sagaEvent, transaction, taskSpec);

        List<SagaTaskSpec> tasks = taskSpec.getNext().stream().map(transactionSpec::getTask).collect(toList());
        generateEvents(txId, tasks);
        writeLog(sagaEvent, transaction, EVENT_END);
        updateTransactionStatus(transaction, transactionSpec);
    }

    private Context initContext(SagaEvent sagaEvent) {
        return transactionRepository
            .findById(sagaEvent.getTransactionId())
            .map(tx -> transactionToContext(sagaEvent, tx))
            .orElse(null);
    }

    private boolean isTransactionExists(SagaEvent sagaEvent, Context context) {
        if (context == null) {
            log.error("Transaction with id {} not found.", sagaEvent.getTransactionId());
            retry(sagaEvent);
            return false;
        }
        return true;
    }

    private boolean isTransactionInCorrectState(SagaEvent sagaEvent, Context context) {
        SagaTransaction transaction = context.getTransaction();
        if (NEW == transaction.getSagaTransactionState()) {
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
            log.warn("Task will not execute. Depends tasks {} not finished. Transaction id {}.", notFinishedTasks, txId);
            retry(sagaEvent);
            return false;
        }
        return true;
    }

    private boolean isCurrentTaskNotFinished(SagaEvent sagaEvent, Context context) {
        SagaTransaction transaction = context.getTransaction();
        SagaTransactionSpec transactionSpec = context.getTransactionSpec();
        if (isTaskFinished(sagaEvent, context.getTxId())) {
            log.warn("Task {} in already finished. Event {} skipped.", transaction, sagaEvent);
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
        return !getNotFinishedTasks(txId, singletonList(sagaEvent.getTypeKey())).isEmpty();
    }

    @Override
    public void cancelSagaEvent(String sagaTxKey) {
        SagaTransaction sagaTransaction = getById(sagaTxKey);
        sagaTransaction.setSagaTransactionState(CANCELED);
        transactionRepository.save(sagaTransaction);
    }

    private void generateFirstEvents(SagaTransaction sagaTransaction) {
        SagaTransactionSpec spec = specService.getTransactionSpec(sagaTransaction.getTypeKey());
        generateEvents(sagaTransaction.getId(), spec.getFirstTasks());
    }

    private void generateEvents(String sagaTransactionId, List<SagaTaskSpec> sagaTaskSpecs) {
        String tenantKey = tenantUtils.getTenantKey();
        sagaTaskSpecs.stream()
            .map(task -> new SagaEvent()
                .setTypeKey(task.getKey())
                .setTenantKey(tenantKey)
                .setTransactionId(sagaTransactionId)
            ).forEach(eventsManager::sendEvent);
    }

    private void updateTransactionStatus(SagaTransaction transaction, SagaTransactionSpec transactionSpec) {
        List<String> txTasks = transactionSpec.getTasks().stream().map(SagaTaskSpec::getKey).collect(toList());
        Collection<String> notFinishedTasks = getNotFinishedTasks(transaction.getId(), txTasks);
        if (notFinishedTasks.isEmpty()) {
            transactionRepository.save(transaction.setSagaTransactionState(FINISHED));
        }
    }

    private void retry(SagaEvent sagaEvent) {
        eventsManager.sendEvent(sagaEvent);
    }

    private void execute(SagaEvent sagaEvent, SagaTransaction transaction, SagaTaskSpec taskSpec) {
        try {
            StopWatch stopWatch = StopWatch.createStarted();
            log.info("Start execute task by event {} transaction {}", sagaEvent, transaction);
            taskExecutor.executeTask(taskSpec, transaction);
            log.info("Finish execute task by event {} transaction {}. Time: {}ms", sagaEvent, transaction, stopWatch.getTime());
        } catch (Exception e) {
            log.error("Error execute task.", e);
            failHandler(transaction, sagaEvent, taskSpec);
        }
    }

    private void failHandler(SagaTransaction transaction, SagaEvent sagaEvent, SagaTaskSpec taskSpec) {
        if (taskSpec.getRetryPolicy() == RETRY) {
            log.info("Using retry strategy.");
            retry(sagaEvent);
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

        List<SagaLog> finished = logRepository.findAll(where((root, query, cb) -> {
            Predicate conjunction = cb.conjunction();
            for (String key : taskKeys) {
                conjunction = cb.and(
                    conjunction,
                    cb.equal(root.get("eventTypeKey"), key),
                    cb.equal(root.get("logType"), EVENT_END),
                    cb.equal(root.get("sagaTransaction").get("id"), sagaTxId)
                );
            }
            return conjunction;
        }));
        Set<String> depends = new HashSet<>(taskKeys);
        finished.forEach(log -> depends.remove(log.getEventTypeKey()));
        return depends;
    }

    private void writeLog(SagaEvent sagaEvent, SagaTransaction transaction, SagaLogType eventType) {
        logRepository.save(
            new SagaLog()
            .setLogType(eventType)
            .setEventTypeKey(sagaEvent.getTypeKey())
            .setSagaTransaction(transaction)
        );
    }

    private SagaTransaction getById(String sagaTxKey) {
        return transactionRepository.findById(sagaTxKey)
            .orElseThrow(() -> new EntityNotFoundException("Transaction by id " + sagaTxKey + " not found"));
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
