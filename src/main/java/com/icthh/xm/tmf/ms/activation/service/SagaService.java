package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState;
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

    SagaEvent getEventById(String eventId);

    List<SagaLog> getLogsByTransaction(String txId);

    Page<SagaTransaction> getAllTransaction(Pageable pageable);

    Optional<SagaTransaction> findTransactionById(String id);

    /**
     * This method restore activation process in a low probability сase when kafka lost events.
     * For example after disk problem, like all disk was full etc.
     */
    void resendEventsByStateInQueue();

    SagaTransaction getByKey(String key);

    void updateEventContext(String eventId, Map<String, Object> context);

    void updateTransactionContext(String id, Map<String, Object> context);

    /**
     * This method change transaction state in separate DB transaction
     *
     * @param txId  - the id of transaction
     * @param state - new state of the transaction
     */
    void changeTransactionState(String txId, SagaTransactionState state);
}
