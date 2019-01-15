package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.commons.lep.spring.LepService;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.resolver.TaskTypeKeyResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@LepService(group = "tasks")
public class SagaTaskExecutor {

    @LogicExtensionPoint(value = "Task", resolver = TaskTypeKeyResolver.class)
    public void executeTask(SagaTaskSpec task, SagaTransaction sagaTransaction) {
        log.error("Script for task {} not found. Transaction {}.", task, sagaTransaction);
    }

}
