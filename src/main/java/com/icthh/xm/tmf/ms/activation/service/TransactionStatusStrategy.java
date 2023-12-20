package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;

import java.util.Map;

public interface TransactionStatusStrategy {
    void updateTransactionStatus(SagaTransaction transaction, SagaTransactionSpec transactionSpec,
                                 Map<String, Object> taskContext);
}
