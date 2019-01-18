package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.commons.lep.spring.LepService;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.resolver.TaskTypeKeyResolver;
import com.icthh.xm.tmf.ms.activation.resolver.TransactionTypeKeyResolver;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@LepService(group = "tasks")
public class SagaTaskExecutor {

    @LogicExtensionPoint(value = "Task", resolver = TaskTypeKeyResolver.class)
    public Map<String, Object> executeTask(SagaTaskSpec task, SagaEvent sagaEvent, SagaTransaction sagaTransaction) {
        log.error("Script for task {} not found. Transaction {}.", task, sagaTransaction);
        return Collections.emptyMap();
    }

    @LogicExtensionPoint(value = "OnFinish", resolver = TransactionTypeKeyResolver.class)
    public void onFinish(SagaTransaction sagaTransaction) {
        log.warn("Script for finish not found. Transaction {}.", sagaTransaction);
    }
}
