package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import com.icthh.xm.tmf.ms.activation.repository.SagaLogRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState.FINISHED;
import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.toList;

@Slf4j
@RequiredArgsConstructor
public class FinishTransactionStrategy implements TransactionStatusStrategy {

    private final SagaTaskExecutor taskExecutor;
    private final SagaTransactionRepository transactionRepository;
    private final SagaLogRepository logRepository;

    public void updateTransactionStatus(SagaTransaction transaction, SagaTransactionSpec transactionSpec,
                                        Map<String, Object> taskContext) {
        if (isAllTaskFinished(transaction, transactionSpec)) {
            if (TRUE.equals(transactionSpec.getRetryOnFinish())) {
                taskExecutor.onFinish(transaction, taskContext);

                transactionRepository.save(transaction.setSagaTransactionState(FINISHED));
            } else {
                transactionRepository.save(transaction.setSagaTransactionState(FINISHED));
                taskExecutor.onFinish(transaction, taskContext);

            }
        }
    }

    protected boolean isAllTaskFinished(SagaTransaction transaction, SagaTransactionSpec transactionSpec) {
        List<String> txTasks = transactionSpec.getTasks().stream().map(SagaTaskSpec::getKey).collect(toList());
        List<String> finished = logRepository.getFinishLogsTypeKeys(transaction.getId(), txTasks);
        log.debug("Finished tasks {} by keys {}", finished, txTasks);
        Set<String> finishedTasks = new HashSet<>(finished);
        return finishedTasks.containsAll(txTasks);
    }
}
