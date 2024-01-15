package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaType;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MapSpecResolver implements SagaSpecResolver {

    private final Map<String, SagaSpec> sagaSpecs = new ConcurrentHashMap<>();

    @Override
    public Optional<SagaTransactionSpec> findTransactionSpec(String tenant, SagaType sagaType) {
        SagaSpec sagaSpec = sagaSpecs.get(tenant);
        return Optional.ofNullable(sagaSpec).map(it -> it.getByType(sagaType.getTypeKey()));
    }

    @Override
    public void update(String tenant, SagaSpec spec) {
        sagaSpecs.put(tenant, spec);
    }

    @Override
    public void remove(String tenant) {
        sagaSpecs.remove(tenant);
    }

    @Override
    public String getActualSpecVersion(String tenantKey) {
        SagaSpec sagaSpec = sagaSpecs.get(tenantKey);
        return sagaSpec != null ? sagaSpec.getVersion() : null;
    }
}