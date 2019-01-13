package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;

public interface SagaService {

    SagaTransaction createNewSaga(SagaTransaction sagaTransaction);
    void onSagaEvent(SagaEvent sagaEvent);
    void cancelSagaEvent(String sagaKey);

}
