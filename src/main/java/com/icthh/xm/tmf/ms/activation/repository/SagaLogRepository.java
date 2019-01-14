package com.icthh.xm.tmf.ms.activation.repository;

import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SagaLogRepository extends JpaRepository<SagaLog, Long>, JpaSpecificationExecutor<SagaLog> {
    List<SagaLog> findByLogTypeAndEventTypeKeyAndSagaTransaction(SagaLogType logType, String taskKey,
                                                                 SagaTransaction transaction);
}
