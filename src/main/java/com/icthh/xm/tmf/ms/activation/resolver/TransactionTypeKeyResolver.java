package com.icthh.xm.tmf.ms.activation.resolver;

import com.icthh.xm.commons.lep.AppendLepKeyResolver;
import com.icthh.xm.lep.api.LepManagerService;
import com.icthh.xm.lep.api.LepMethod;
import com.icthh.xm.lep.api.commons.SeparatorSegmentedLepKey;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.service.SagaSpecService;
import org.springframework.stereotype.Component;

@Component
public class TransactionTypeKeyResolver extends GroupLepKeyResolver {

    public TransactionTypeKeyResolver(SagaSpecService sagaSpecService) {
        super(sagaSpecService);
    }

    @Override
    protected String[] getAppendSegments(LepMethod method, SagaTransaction sagaTransaction) {
        String translatedSagaTransactionKey = translateToLepConvention(sagaTransaction.getTypeKey());
        return new String[] {translatedSagaTransactionKey};
    }
}
