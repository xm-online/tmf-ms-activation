package com.icthh.xm.tmf.ms.activation.repository;

import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaTransactionRepository extends JpaRepository<SagaTransaction, String> {

    Page<SagaTransaction> findAllBySagaTransactionState(SagaTransactionState sagaTransactionState, Pageable pageable);
}
