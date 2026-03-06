package com.icthh.xm.tmf.ms.activation.events.bindings;

import static com.icthh.xm.commons.config.client.repository.TenantListRepository.TENANTS_LIST_CONFIG_KEY;
import static org.apache.commons.lang3.StringUtils.upperCase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.commons.config.client.api.RefreshableConfiguration;
import com.icthh.xm.commons.config.domain.TenantState;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

@Slf4j
@RequiredArgsConstructor
public class MessagingConfiguration implements RefreshableConfiguration, ApplicationListener<ApplicationReadyEvent> {

    private final ObjectMapper objectMapper;
    private final ActivationDynamicTopicConsumerConfiguration activationDynamicTopicConsumerConfiguration;
    private final Set<String> appTenantKeys = ConcurrentHashMap.newKeySet();

    @Value("${spring.application.name}")
    private String appName;

    @Autowired
    public MessagingConfiguration(ActivationDynamicTopicConsumerConfiguration activationDynamicTopicConsumerConfiguration,
                                  ObjectMapper objectMapper) {
        this.activationDynamicTopicConsumerConfiguration = activationDynamicTopicConsumerConfiguration;
        this.objectMapper = objectMapper;
    }

    @SneakyThrows
    private void updateTenants(String key, String config) {
        log.info("Tenants list was updated");

        if (!TENANTS_LIST_CONFIG_KEY.equals(key)) {
            throw new IllegalArgumentException("Wrong config key to update " + key);
        }

        TypeReference<Map<String, Set<TenantState>>> typeRef = new TypeReference<>() {};
        Map<String, Set<TenantState>> tenantsByServiceMap = objectMapper.readValue(config, typeRef);
        Set<TenantState> tenantKeys = tenantsByServiceMap.get(appName);
        tenantKeys.stream().map(TenantState::getName).forEach(it -> {
            appTenantKeys.add(it);
            createChannels(it);
        });
    }

    @Override
    public void onRefresh(String key, String config) {
        updateTenants(key, config);
    }

    @Override
    public boolean isListeningConfiguration(String updatedKey) {
        return TENANTS_LIST_CONFIG_KEY.equals(updatedKey);
    }

    @Override
    public void onInit(String key, String config) {
        updateTenants(key, config);
    }

    private void createChannels(String tenantName) {
        String tenantKey = upperCase(tenantName);
        activationDynamicTopicConsumerConfiguration.buildDynamicConsumers(tenantKey);
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        appTenantKeys.forEach(tenantName -> {
            String tenantKey = upperCase(tenantName);
            activationDynamicTopicConsumerConfiguration.sendRefreshDynamicConsumersEvent(tenantKey);
        });
    }
}
