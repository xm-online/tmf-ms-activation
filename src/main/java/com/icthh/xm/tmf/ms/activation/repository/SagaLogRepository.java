package com.icthh.xm.tmf.ms.activation.repository;

import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_END;
import static org.springframework.data.jpa.domain.Specification.where;

import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import javax.persistence.criteria.Predicate;
import java.util.List;

public interface SagaLogRepository extends JpaRepository<SagaLog, Long>, JpaSpecificationExecutor<SagaLog> {
    default List<SagaLog> getFinishLogs(String sagaTxId, List<String> taskKeys) {
        return findAll(where((root, query, cb) -> {
            Predicate conjunction = cb.disjunction();
            for (String key : taskKeys) {
                conjunction = cb.or(
                    conjunction,
                    cb.and(
                        cb.equal(root.get("eventTypeKey"), key),
                        cb.equal(root.get("logType"), EVENT_END),
                        cb.equal(root.get("sagaTransaction").get("id"), sagaTxId)
                    )
                );
            }
            return conjunction;
        }));
    }

    List<SagaLog> findByLogTypeAndEventTypeKeyAndSagaTransaction(SagaLogType eventType, String typeKey,
                                                        SagaTransaction transaction);

    default List<SagaLog> findLogs(SagaLogType eventStart, SagaTransaction transaction, String typeKey) {
        return findByLogTypeAndEventTypeKeyAndSagaTransaction(eventStart, typeKey, transaction);
    }

    List<SagaLog> findBySagaTransactionId(String txId);
}
