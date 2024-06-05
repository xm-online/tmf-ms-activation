package com.icthh.xm.tmf.ms.activation.config;

import com.icthh.xm.commons.lep.TargetProceedingLep;
import com.icthh.xm.commons.lep.api.BaseLepContext;
import com.icthh.xm.commons.lep.api.LepAdditionalContext;
import com.icthh.xm.commons.lep.api.LepAdditionalContextField;
import com.icthh.xm.commons.lep.api.LepBaseKey;
import com.icthh.xm.commons.lep.api.LepEngine;
import com.icthh.xm.tmf.ms.activation.config.TransactionLepAdditionalContext.TransactionContext;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static com.icthh.xm.tmf.ms.activation.config.TransactionLepAdditionalContext.TransactionContextField.FIELD_NAME;


@Component
@RequiredArgsConstructor
public class TransactionLepAdditionalContext implements LepAdditionalContext<TransactionContext> {


    @Override
    public String additionalContextKey() {
        return FIELD_NAME;
    }

    @Override
    public TransactionContext additionalContextValue() {
        return null;
    }

    @Override
    public Optional<TransactionContext> additionalContextValue(BaseLepContext lepContext, LepEngine lepEngine, TargetProceedingLep lepMethod) {
        LepBaseKey lepBaseKey = lepMethod.getLepBaseKey();
        if ("tasks".equals(lepBaseKey.getGroup()) && "Task".equals(lepBaseKey.getBaseKey())) {
            SagaTransaction sagaTransaction = lepMethod.getParameter("sagaTransaction", SagaTransaction.class);
            TransactionContext transactionContext = new TransactionContext(sagaTransaction.getContext());
            return Optional.of(transactionContext);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Class<? extends LepAdditionalContextField> fieldAccessorInterface() {
        return TransactionContextField.class;
    }

    public interface TransactionContextField extends LepAdditionalContextField {
        String FIELD_NAME = "transaction";
        default TransactionContext getTransaction() {
            return (TransactionContext)get(FIELD_NAME);
        }
    }

    // need to implement all methods from Map interface for js lep-s interop
    @RequiredArgsConstructor
    public static class TransactionContext implements Map<String, Object> {

        @Delegate
        private final Map<String, Object> delegate;

    }
}
