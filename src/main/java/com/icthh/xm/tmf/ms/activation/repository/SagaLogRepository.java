package com.icthh.xm.tmf.ms.activation.repository;

import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_END;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.REJECTED_BY_CONDITION;
import static org.springframework.data.jpa.domain.Specification.where;

import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.criteria.Predicate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SagaLogRepository extends JpaRepository<SagaLog, Long>, JpaSpecificationExecutor<SagaLog> {

    @Query("SELECT s FROM SagaLog s " +
        "WHERE (s.logType = 'REJECTED_BY_CONDITION' OR s.logType = 'EVENT_END') " +
        "AND s.sagaTransaction.id = :sagaTransactionId AND s.eventTypeKey IN :eventTypeKeys " +
        "AND s.iteration IS NULL")
    List<SagaLog> getFinishLogs(
        @Param("sagaTransactionId") String sagaTransactionId,
        @Param("eventTypeKeys") List<String> eventTypeKeys
    );

    @Query("SELECT s.eventTypeKey FROM SagaLog s " +
        "WHERE (s.logType = 'REJECTED_BY_CONDITION' OR s.logType = 'EVENT_END') " +
        "AND s.sagaTransaction.id = :sagaTransactionId AND s.eventTypeKey IN :eventTypeKeys " +
        "AND s.iteration IS NULL")
    List<String> getFinishLogsTypeKeys(
        @Param("sagaTransactionId") String sagaTransactionId,
        @Param("eventTypeKeys") List<String> eventTypeKeys
    );

    @Query("SELECT s FROM SagaLog s " +
        "WHERE s.sagaTransaction.id = :sagaTransactionId AND s.eventTypeKey = :eventTypeKey " +
        "AND s.iteration IS NULL")
    List<SagaLog> getLogsBySagaTransactionIdAndTypeKey(
        @Param("sagaTransactionId") String sagaTransactionId,
        @Param("eventTypeKey") String eventTypeKey
    );

    @Query("SELECT s FROM SagaLog s " +
        "WHERE (s.logType = 'REJECTED_BY_CONDITION' OR s.logType = 'EVENT_END') " +
        "AND s.sagaTransaction.id = :sagaTransactionId AND s.eventTypeKey = :eventTypeKey " +
        "AND (s.iteration = :iteration OR (s.iteration is null AND :iteration = null))")
    Optional<SagaLog> findFinishLogTypeKeyAndIteration(
        @Param("sagaTransactionId") String sagaTransactionId,
        @Param("eventTypeKey") String eventTypeKey,
        @Param("iteration") Integer iteration
    );

    List<SagaLog> findByLogTypeAndEventTypeKeyAndSagaTransactionAndIteration(SagaLogType eventType, String typeKey,
                                                                             SagaTransaction transaction, Integer iteration);

    default List<SagaLog> findLogs(SagaLogType eventType, SagaTransaction transaction, String typeKey, Integer iteration) {
        return findByLogTypeAndEventTypeKeyAndSagaTransactionAndIteration(eventType, typeKey, transaction, iteration);
    }

    @Query("SELECT s.taskContext FROM SagaLog s " +
        "WHERE (s.logType = 'REJECTED_BY_CONDITION' OR s.logType = 'EVENT_END') AND s.eventTypeKey = :typeKey AND s.sagaTransaction.id = :transactionId " +
        "ORDER BY s.iteration ASC")
    List<Map<String, Object>> getResultTaskContexts(@Param("typeKey") String typeKey, @Param("transactionId") String transactionId);

    List<SagaLog> findBySagaTransactionId(String txId);

    @Query("SELECT count(s.iteration) FROM SagaLog s " +
        "WHERE (s.logType = 'REJECTED_BY_CONDITION' OR s.logType = 'EVENT_END') AND s.sagaTransaction.id = :sagaTransactionId AND s.eventTypeKey = :eventTypeKey ")
    Integer countByIterableLogs(String sagaTransactionId, String eventTypeKey);

    SagaLog findBySagaTransactionIdAndEventTypeKeyAndLogTypeAndIterationIsNull(String txId, String eventTypeKey, SagaLogType logType);
}
