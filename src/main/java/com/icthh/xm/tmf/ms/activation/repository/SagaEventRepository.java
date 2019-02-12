package com.icthh.xm.tmf.ms.activation.repository;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SagaEventRepository extends JpaRepository<SagaEvent, String>, JpaSpecificationExecutor<SagaEvent> {
    List<SagaEvent> findByStatus(SagaEventType onRetry);

    List<SagaEvent> findByTransactionId(String txId);
}
