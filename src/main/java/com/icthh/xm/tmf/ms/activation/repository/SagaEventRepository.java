package com.icthh.xm.tmf.ms.activation.repository;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SagaEventRepository extends JpaRepository<SagaEvent, String>, JpaSpecificationExecutor<SagaEvent> {
    List<SagaEvent> findByStatus(SagaEventStatus onRetry);

    List<SagaEvent> findByTransactionId(String txId);
}
