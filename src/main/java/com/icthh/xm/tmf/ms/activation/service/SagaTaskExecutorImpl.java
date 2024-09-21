package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/** We need proxy class to be able to use override this bean.
 * LepService mark class spring bean, and to make SagaTaskExecutor flexible we need this proxy. */
@Slf4j
@RequiredArgsConstructor
public class SagaTaskExecutorImpl implements SagaTaskExecutor {

    private final SagaTaskLepExecutor sagaTaskLepExecutor;

    @Override
    public Map<String, Object> executeTask(SagaTaskSpec task, SagaEvent sagaEvent, SagaTransaction sagaTransaction, Continuation continuation) {
        return sagaTaskLepExecutor.executeTask(task, sagaEvent, sagaTransaction, continuation);
    }

    @Override
    public void onFinish(SagaTransaction sagaTransaction, Map<String, Object> taskContext) {
        sagaTaskLepExecutor.onFinish(sagaTransaction, taskContext);
    }

    @Override
    public boolean onCheckWaitCondition(SagaTaskSpec task, SagaEvent sagaEvent, SagaTransaction sagaTransaction) {
        return sagaTaskLepExecutor.onCheckWaitCondition(task, sagaEvent, sagaTransaction);
    }

    @Override
    public boolean continueIterableLoopCondition(SagaTaskSpec task, SagaEvent sagaEvent, SagaTransaction sagaTransaction) {
        return sagaTaskLepExecutor.continueIterableLoopCondition(task, sagaEvent, sagaTransaction);
    }
}
