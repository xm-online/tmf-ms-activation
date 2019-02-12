package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SagaService {

    SagaTransaction createNewSaga(SagaTransaction sagaTransaction);

    void onSagaEvent(SagaEvent sagaEvent);

    void continueTask(String taskId, Map<String, Object> taskContext);

    void cancelSagaTransaction(String sagaTxKey);

    Page<SagaTransaction> getAllNewTransaction(Pageable pageable);

    void retrySagaEvent(String txid, String eventId);

    List<SagaEvent> getEventsByTransaction(String txId);

    List<SagaLog> getLogsByTransaction(String txId);

    Page<SagaTransaction> getAllTransaction(Pageable pageable);
}
