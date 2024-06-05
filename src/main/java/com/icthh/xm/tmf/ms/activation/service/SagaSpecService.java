package com.icthh.xm.tmf.ms.activation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.client.api.RefreshableConfiguration;
import com.icthh.xm.commons.exceptions.BusinessException;
import com.icthh.xm.commons.logging.aop.IgnoreLogginAspect;
import com.icthh.xm.tmf.ms.activation.domain.SagaType;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaSpec;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.Optional;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaSpecService implements RefreshableConfiguration {

    private static final String TENANT_NAME = "tenantName";
    private static final String PATH_PATTERN = "/config/tenants/{tenantName}/activation/activation-spec.yml";
    private static final String FOLDER_PATH_PATTERN = "/config/tenants/{tenantName}/activation/activation-specs/*.yml";

    private final TenantUtils tenantUtils;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final SagaSpecResolver sagaSpecResolver;

    @Override
    public boolean isListeningConfiguration(String updatedKey) {
        return matcher.match(PATH_PATTERN, updatedKey) || matcher.match(FOLDER_PATH_PATTERN, updatedKey);
    }

    @Override
    public void onRefresh(String updatedKey, String config) {
        try {
            String tenant = extractTenant(updatedKey);
            if (StringUtils.isBlank(config)) {
                sagaSpecResolver.remove(tenant, updatedKey);
                log.info("Spec for tenant '{}' were removed: {}", tenant, updatedKey);
            } else {
                SagaSpec spec = mapper.readValue(config, SagaSpec.class);
                if (spec == null) {
                    sagaSpecResolver.remove(tenant, updatedKey);
                    log.info("Spec for tenant '{}' were removed: {}", tenant, updatedKey);
                } else {
                    updateRetryPolicy(spec);
                    sagaSpecResolver.update(tenant, updatedKey, spec);
                    log.info("Spec for tenant '{}' were updated: {}", tenant, updatedKey);
                }
            }
        } catch (Exception e) {
            log.error("Error read specification from path: {}", updatedKey, e);
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
        if (matcher.match(PATH_PATTERN, updatedKey)) {
            return matcher.extractUriTemplateVariables(PATH_PATTERN, updatedKey).get(TENANT_NAME);
        }
        if (matcher.match(FOLDER_PATH_PATTERN, updatedKey)) {
            return matcher.extractUriTemplateVariables(FOLDER_PATH_PATTERN, updatedKey).get(TENANT_NAME);
        }
        throw new BusinessException("error.invalid.path", "Invalid path: " + updatedKey);
    }

    public SagaSpec getActualSagaSpec() {
        return sagaSpecResolver.getActualSagaSpec(tenantUtils.getTenantKey());
    }

    @IgnoreLogginAspect
    public SagaTransactionSpec getTransactionSpec(SagaType sagaType) {
        return findTransactionSpec(sagaType).orElseThrow(() ->
                new InvalidSagaSpecificationException("saga.spec.not.found",
                    "Saga spec for type " + sagaType.getTypeKey() + " and tenant " + tenantUtils.getTenantKey() + " not found.")
        );
    }

    @IgnoreLogginAspect
    public Optional<SagaTransactionSpec> findTransactionSpec(SagaType sagaType) {
        return sagaSpecResolver.findTransactionSpec(tenantUtils.getTenantKey(), sagaType);
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
