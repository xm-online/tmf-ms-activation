package com.icthh.xm.tmf.ms.activation.repository;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SagaEventRepository extends JpaRepository<SagaEvent, String>, JpaSpecificationExecutor<SagaEvent> {
    List<SagaEvent> findByStatus(SagaEventStatus status);

    List<SagaEvent> findByTransactionId(String txId);

    Optional<SagaEvent> findByTransactionIdAndTypeKey(String transactionId, String typeKey);

    long countByStatus(SagaEventStatus status);

    long countByStatusAndCreateDateBefore(SagaEventStatus status, Instant date);

    List<SagaEvent> findByTransactionIdAndTypeKeyIn(String transactionId, Collection<String> dependentTasks);
}
