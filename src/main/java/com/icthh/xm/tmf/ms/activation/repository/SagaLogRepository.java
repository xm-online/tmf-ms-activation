package com.icthh.xm.tmf.ms.activation.repository;

import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SagaLogRepository extends JpaRepository<SagaLog, Long>, JpaSpecificationExecutor<SagaLog> {



}
