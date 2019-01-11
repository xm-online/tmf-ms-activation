package com.icthh.xm.tmf.ms.activation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.client.api.RefreshableConfiguration;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaSpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SagaSpecService implements RefreshableConfiguration {

    private static final String TENANT_NAME = "tenantName";
    private static final String PATH_PATTERN = "/config/tenants/{tenantName}/activation/transaction-spec.yml";;

    private AntPathMatcher matcher = new AntPathMatcher();
    private ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    private final Map<String, SagaSpec> sagaSpecs = new ConcurrentHashMap<>();

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

    private void refreshConfig(String updatedKey, String config){
        try {
            String tenant = extractTenant(updatedKey);
            if (StringUtils.isBlank(config)) {
                sagaSpecs.remove(tenant);
                log.info("Spec for tenant '{}' were removed: {}", tenant, updatedKey);
            } else {
                SagaSpec spec = mapper.readValue(config, SagaSpec.class);
                sagaSpecs.put(tenant, spec);
                log.info("Spec for tenant '{}' were updated: {}", tenant, updatedKey);
            }
        } catch (Exception e) {
            log.error("Error read Scheduler specification from path: {}", updatedKey, e);
        }
    }

    private String extractTenant(final String updatedKey) {
        return matcher.extractUriTemplateVariables(PATH_PATTERN, updatedKey).get(TENANT_NAME);
    }

}
