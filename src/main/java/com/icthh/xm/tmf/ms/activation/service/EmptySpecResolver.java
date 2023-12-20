package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaType;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;

import java.util.Optional;

public class EmptySpecResolver implements SagaSpecResolver {
    @Override
    public Optional<SagaTransactionSpec> findTransactionSpec(SagaType sagaType) {
        return Optional.empty();
    }
}