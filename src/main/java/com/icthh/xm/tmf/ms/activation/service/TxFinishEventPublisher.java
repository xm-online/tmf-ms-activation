package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import java.util.Map;

public interface TxFinishEventPublisher {
    default void emitEvent(SagaTransaction transaction, Map<String, Object> lastTaskContext) {
        emitEvent(new OnTxFinishEvent(this, transaction, lastTaskContext));
    }

    void emitEvent(OnTxFinishEvent event);
}
