package com.icthh.xm.tmf.ms.activation.resolver;

import com.icthh.xm.lep.api.LepMethod;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.service.SagaSpecService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TaskTypeKeyResolver extends GroupLepKeyResolver {

    public TaskTypeKeyResolver(SagaSpecService sagaSpecService) {
        super(sagaSpecService);
    }

    @Override
    public List<String> segments(LepMethod method) {
        SagaTransaction sagaTransaction = method.getParameter("sagaTransaction", SagaTransaction.class);
        SagaTaskSpec task = method.getParameter("task", SagaTaskSpec.class);
        return List.of(sagaTransaction.getTypeKey(), task.getKey());
    }
}
