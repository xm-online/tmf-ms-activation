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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;
import java.util.List;

import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_END;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_START;
import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.NEW;
import static java.util.stream.Collectors.toList;
import static org.springframework.data.jpa.domain.Specification.where;

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
        writeLog(sagaEvent, transaction, EVENT_START);

        SagaTransactionSpec transactionSpec = sagaSpecService.getTransactionSpec(transaction.getTypeKey());
        SagaTaskSpec taskSpec = transactionSpec.getTask(sagaEvent.getTypeKey());
        execute(taskSpec);

        List<SagaTaskSpec> tasks = taskSpec.getNext().stream().map(transactionSpec::getTask).collect(toList());
        generateEvents(transaction.getId(), tasks);

        writeLog(sagaEvent, transaction, EVENT_END);
    }

    private void execute(SagaTaskSpec taskSpec) {
        try {
            taskExecutor.executeTask(taskSpec);
        } catch (Exception e) {

        }
    }

    private boolean isAllDependsTasksFinished(SagaTaskSpec taskSpec) {
        if (taskSpec.getDepends().isEmpty()) {
            return true;
        }
        sagaLogRepository.findAll(where((root, query, cb) -> {
            Predicate conjunction = cb.conjunction();
            for (String key: taskSpec.getDepends()) {
                conjunction = cb.and(
                    conjunction,
                    cb.equal(root.get(SagaLog_), key),
                    cb.equal(, EVENT_END)
                 );
            }
            return conjunction;
        }));

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
