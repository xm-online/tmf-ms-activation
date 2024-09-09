package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.commons.lep.spring.LepService;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.resolver.TaskTypeKeyResolver;
import com.icthh.xm.tmf.ms.activation.resolver.TransactionTypeKeyResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Slf4j
@LepService(group = "tasks")
public class SagaTaskExecutorImpl implements SagaTaskExecutor {

    @Override
    @LogicExtensionPoint(value = "Task", resolver = TaskTypeKeyResolver.class)
    public Map<String, Object> executeTask(SagaTaskSpec task, SagaEvent sagaEvent, SagaTransaction sagaTransaction, Continuation continuation) {
        log.error("Script for task {} not found. Transaction {}.", task, sagaTransaction);
        return Collections.emptyMap();
    }

    @Override
    @LogicExtensionPoint(value = "OnFinish", resolver = TransactionTypeKeyResolver.class)
    public void onFinish(SagaTransaction sagaTransaction, Map<String, Object> taskContext) {
        log.info("Script for finish not found. Transaction {}.", sagaTransaction);
    }

    @Override
    @LogicExtensionPoint(value = "OnCheckWaitCondition", resolver = TaskTypeKeyResolver.class)
    public boolean onCheckWaitCondition(SagaTaskSpec task, SagaEvent sagaEvent, SagaTransaction sagaTransaction) {
        return true;
    }

    @Override
    @LogicExtensionPoint(value = "ContinueIterableLoopCondition", resolver = TaskTypeKeyResolver.class)
    public boolean continueIterableLoopCondition(SagaTaskSpec task, SagaEvent sagaEvent, SagaTransaction sagaTransaction) {
        return false;
    }
}
