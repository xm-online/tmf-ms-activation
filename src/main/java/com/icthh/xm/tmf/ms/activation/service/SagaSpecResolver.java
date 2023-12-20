package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaType;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;

import java.util.Optional;

public interface SagaSpecResolver {
    Optional<SagaTransactionSpec> findTransactionSpec(SagaType sagaType);
}
