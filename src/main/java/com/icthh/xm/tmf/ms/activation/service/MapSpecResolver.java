package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaType;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MapSpecResolver implements SagaSpecResolver {

    private final Map<String, SagaSpec> sagaSpecs = new ConcurrentHashMap<>();
    private final Map<String, Map<String, SagaSpec>> tenantToFileToSagaSpecs = new ConcurrentHashMap<>();

    @Override
    public Optional<SagaTransactionSpec> findTransactionSpec(String tenant, SagaType sagaType) {
        SagaSpec sagaSpec = sagaSpecs.get(tenant);
        return Optional.ofNullable(sagaSpec).map(it -> it.getByType(sagaType.getTypeKey()));
    }

    @Override
    public void update(String tenant, String updatedKey, SagaSpec spec) {
        Map<String, SagaSpec> tenantMap = getTenantMap(tenant);
        tenantMap.put(updatedKey, spec);
        updateRetryPolicy(spec);
        updateTenantSpec(tenant);
    }

    private void updateRetryPolicy(SagaSpec spec) {
        spec.getTransactions().forEach(tx -> tx.setTasks(
            tx.getTasks().stream().peek(
                task -> task.applyAsDefaultTransactionConfig(tx)
            ).collect(Collectors.toList())
        ));
    }

    private void updateTenantSpec(String tenant) {
        Map<String, SagaSpec> tenantMap = getTenantMap(tenant);
        tenantMap.values().stream().reduce(SagaSpec::mergeSpec).ifPresent(it -> sagaSpecs.put(tenant, it));
    }

    @Override
    public void remove(String tenant, String updatedKey) {
        Map<String, SagaSpec> tenantMap = getTenantMap(tenant);
        tenantMap.remove(updatedKey);
        updateTenantSpec(tenant);
    }

    public Map<String, SagaSpec> getTenantMap(String tenant) {
        return tenantToFileToSagaSpecs.computeIfAbsent(tenant, k -> new ConcurrentHashMap<>());
    }

    @Override
    public SagaSpec getActualSagaSpec(String tenantKey) {
        return sagaSpecs.get(tenantKey);
    }
}