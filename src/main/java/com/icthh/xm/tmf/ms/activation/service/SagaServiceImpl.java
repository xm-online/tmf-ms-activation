package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsManager;
import com.icthh.xm.tmf.ms.activation.repository.SagaLogRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_END;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_START;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.*;
import static java.util.stream.Collectors.toList;
import static org.springframework.data.jpa.domain.Specification.where;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaServiceImpl implements SagaService {

    private final SagaLogRepository sagaLogRepository;
    private final SagaTransactionRepository sagaTransactionRepository;
    private final SagaSpecService sagaSpecService;
    private final EventsManager eventsManager;
    private final TenantUtils tenantUtils;
    private final TaskExecutor taskExecutor;

    @Override
    @Transactional
    public SagaTransaction createNewSaga(SagaTransaction sagaTransaction) {
        sagaTransaction.setId(null);
        sagaTransaction.setSagaTransactionState(NEW);
        SagaTransaction saved = sagaTransactionRepository.save(sagaTransaction);
        generateFirstEvents(sagaTransaction);
        return saved;
    }

    private void generateFirstEvents(SagaTransaction sagaTransaction) {
        SagaTransactionSpec spec = sagaSpecService.getTransactionSpec(sagaTransaction.getTypeKey());
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

    @Override
    public void onSagaEvent(SagaEvent sagaEvent) {
        SagaTransaction transaction = sagaTransactionRepository.getOne(sagaEvent.getTransactionId());
        if (transaction.getSagaTransactionState() != NEW) {
            log.warn("Transaction in incorrect state {}.", transaction);
            return;
        }

        SagaTransactionSpec transactionSpec = sagaSpecService.getTransactionSpec(transaction.getTypeKey());
        SagaTaskSpec taskSpec = transactionSpec.getTask(sagaEvent.getTypeKey());
        if (isAllDependsTasksFinished(taskSpec, transaction.getId())) {
            writeLog(sagaEvent, transaction, EVENT_START);
            execute(sagaEvent, transaction, taskSpec);
        } else {
            failHandler(sagaEvent);
        }

        List<SagaTaskSpec> tasks = taskSpec.getNext().stream().map(transactionSpec::getTask).collect(toList());
        generateEvents(transaction.getId(), tasks);

        writeLog(sagaEvent, transaction, EVENT_END);
    }

    private void execute(SagaEvent sagaEvent, SagaTransaction transaction, SagaTaskSpec taskSpec) {
        try {
            StopWatch stopWatch = StopWatch.createStarted();
            log.info("Start execute task by event {} transaction {}", sagaEvent, transaction);
            taskExecutor.executeTask(taskSpec, transaction);
            log.info("Finish execute task by event {} transaction {}. Time: {}ms", sagaEvent, transaction, stopWatch.getTime());
        } catch (Exception e) {
            log.error("Error execute task.", e);
            failHandler(sagaEvent);
        }
    }

    private void failHandler(SagaEvent sagaEvent) {
        eventsManager.sendEvent(sagaEvent);
    }

    private boolean isAllDependsTasksFinished(SagaTaskSpec taskSpec, String sagaTxId) {
        if (taskSpec.getDepends().isEmpty()) {
            log.info("No depends tasks. Task will be execute.");
            return true;
        }

        List<SagaLog> all = sagaLogRepository.findAll(where((root, query, cb) -> {
            Predicate conjunction = cb.conjunction();
            for (String key : taskSpec.getDepends()) {
                conjunction = cb.and(
                    conjunction,
                    cb.equal(root.get("eventTypeKey"), key),
                    cb.equal(root.get("logType"), EVENT_END),
                    cb.equal(root.get("sagaTransaction").get("id"), sagaTxId)
                );
            }
            return conjunction;
        }));

        Set<String> depends = new HashSet<>(taskSpec.getDepends());
        all.forEach(depends::remove);
        if (!depends.isEmpty()) {
            log.warn("Task will not execute. Depends tasks {} not finished. Transaction id {}.", depends, sagaTxId);
        }
        return depends.isEmpty();
    }

    private void writeLog(SagaEvent sagaEvent, SagaTransaction transaction, SagaLogType eventType) {
        sagaLogRepository.save(
            new SagaLog()
            .setLogType(eventType)
            .setEventTypeKey(sagaEvent.getTypeKey())
            .setSagaTransaction(transaction)
        );
    }

    @Override
    public void cancelSagaEvent(String sagaKey) {

    }

}
