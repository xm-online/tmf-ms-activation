package com.icthh.xm.tmf.ms.activation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.client.api.RefreshableConfiguration;
import com.icthh.xm.commons.exceptions.BusinessException;
import com.icthh.xm.commons.logging.aop.IgnoreLogginAspect;
import com.icthh.xm.tmf.ms.activation.config.SagaTransactionSpecificationMetric;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaSpecService implements RefreshableConfiguration {

    private static final String TENANT_NAME = "tenantName";
    private static final String PATH_PATTERN = "/config/tenants/{tenantName}/activation/activation-spec.yml";

    private final Map<String, SagaSpec> sagaSpecs = new ConcurrentHashMap<>();
    private final TenantUtils tenantUtils;
    private AntPathMatcher matcher = new AntPathMatcher();
    private ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final SagaTransactionSpecificationMetric sagaTransactionSpecificationMetric;

    @Override
    public void onRefresh(String updatedKey, String config) {
        refreshConfig(updatedKey, config);
    }

    @Override
    public boolean isListeningConfiguration(String updatedKey) {
        return matcher.match(PATH_PATTERN, updatedKey);
    }

    @Override
    public void onInit(String key, String config) {
        if (isListeningConfiguration(key)) {
            refreshConfig(key, config);
        }
    }

    private void refreshConfig(String updatedKey, String config) {
        try {
            String tenant = extractTenant(updatedKey);
            if (StringUtils.isBlank(config)) {
                sagaSpecs.remove(tenant);
                log.info("Spec for tenant '{}' were removed: {}", tenant, updatedKey);
            } else {
                SagaSpec spec = mapper.readValue(config, SagaSpec.class);
                sagaSpecs.put(tenant, spec);
                updateRetryPolicy(spec);
                log.info("Spec for tenant '{}' were updated: {}", tenant, updatedKey);

                initMetrics(tenant, spec);
            }
        } catch (Exception e) {
            log.error("Error read specification from path: {}", updatedKey, e);
        }
    }

    private void initMetrics(String tenant, SagaSpec spec) {
        try {
            List<String> specificationKeys = spec.getTransactions().stream()
                .map(SagaTransactionSpec::getKey)
                .collect(Collectors.toList());
            sagaTransactionSpecificationMetric.initMetrics(tenant, specificationKeys);
        } catch (Exception e) {
            log.error("Could not init metrics for tenant: {}", tenant, e);
        }
    }

    private void updateRetryPolicy(SagaSpec spec) {
        spec.getTransactions().forEach(tx -> tx.setTasks(
            tx.getTasks().stream().peek(
                task -> task.applyAsDefaultTransactionConfig(tx)
            ).collect(Collectors.toList())
        ));
    }

    private String extractTenant(final String updatedKey) {
        return matcher.extractUriTemplateVariables(PATH_PATTERN, updatedKey).get(TENANT_NAME);
    }

    @IgnoreLogginAspect
    public SagaTransactionSpec getTransactionSpec(String typeKey) {
        String tenantKey = tenantUtils.getTenantKey();
        SagaSpec sagaSpec = sagaSpecs.get(tenantKey);
        if (sagaSpec == null) {
            throw new InvalidSagaSpecificationException("saga.spec.not.found",
                "Saga spec for type " + typeKey + " and tenant " + tenantKey + " not found.");
        }
        return sagaSpec.getByType(typeKey);
    }

    @IgnoreLogginAspect
    public Optional<SagaTransactionSpec> findTransactionSpec(String typeKey) {
        String tenantKey = tenantUtils.getTenantKey();
        SagaSpec sagaSpec = sagaSpecs.get(tenantKey);
        return Optional.ofNullable(sagaSpec).map(it -> it.getByType(typeKey));
    }

    public static class InvalidSagaSpecificationException extends BusinessException {
        public InvalidSagaSpecificationException(String message) {
            super(message);
        }

        public InvalidSagaSpecificationException(String code, String message) {
            super(code, message);
        }
    }

}
