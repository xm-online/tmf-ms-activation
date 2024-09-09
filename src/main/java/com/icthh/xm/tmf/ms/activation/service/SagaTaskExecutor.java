package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;

import java.util.Map;

public interface SagaTaskExecutor {

    Map<String, Object> executeTask(SagaTaskSpec task, SagaEvent sagaEvent, SagaTransaction sagaTransaction, Continuation continuation);

    void onFinish(SagaTransaction sagaTransaction, Map<String, Object> taskContext);

    boolean onCheckWaitCondition(SagaTaskSpec task, SagaEvent sagaEvent, SagaTransaction sagaTransaction);

    boolean continueIterableLoopCondition(SagaTaskSpec task, SagaEvent sagaEvent, SagaTransaction sagaTransaction);
}
