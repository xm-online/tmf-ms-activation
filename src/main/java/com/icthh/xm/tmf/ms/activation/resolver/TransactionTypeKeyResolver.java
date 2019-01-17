package com.icthh.xm.tmf.ms.activation.resolver;

import com.icthh.xm.commons.lep.AppendLepKeyResolver;
import com.icthh.xm.lep.api.LepManagerService;
import com.icthh.xm.lep.api.LepMethod;
import com.icthh.xm.lep.api.commons.SeparatorSegmentedLepKey;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import org.springframework.stereotype.Component;

@Component
public class TransactionTypeKeyResolver extends AppendLepKeyResolver {
    @Override
    protected String[] getAppendSegments(SeparatorSegmentedLepKey baseKey, LepMethod method, LepManagerService managerService) {
        SagaTransaction sagaTransaction = getRequiredParam(method, "sagaTransaction", SagaTransaction.class);
        String translatedSagaTransactionKey = translateToLepConvention(sagaTransaction.getTypeKey());
        return new String[] {
            translatedSagaTransactionKey
        };
    }
}
