package com.icthh.xm.tmf.ms.activation.resolver;

import com.icthh.xm.lep.api.LepMethod;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.service.SagaSpecService;
import org.springframework.stereotype.Component;

@Component
public class TaskTypeKeyResolver extends GroupLepKeyResolver {

    public TaskTypeKeyResolver(SagaSpecService sagaSpecService) {
        super(sagaSpecService);
    }

    protected String[] getAppendSegments(LepMethod method, SagaTransaction sagaTransaction) {
        SagaTaskSpec task = getRequiredParam(method, "task", SagaTaskSpec.class);
        String translatedSagaTransactionKey = translateToLepConvention(sagaTransaction.getTypeKey());
        String translatedTaskKey = translateToLepConvention(task.getKey());
        return new String[] {translatedSagaTransactionKey, translatedTaskKey};
    }
}
