package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import java.util.Map;

public interface SagaService {

    SagaTransaction createNewSaga(SagaTransaction sagaTransaction);

    void onSagaEvent(SagaEvent sagaEvent);

    void continueTask(String taskId, Map<String, Object> taskContext);

    void cancelSagaEvent(String sagaTxKey);

}
