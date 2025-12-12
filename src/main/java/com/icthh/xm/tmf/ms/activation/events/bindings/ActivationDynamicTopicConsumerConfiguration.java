package com.icthh.xm.tmf.ms.activation.events.bindings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.commons.topic.domain.DynamicConsumer;
import com.icthh.xm.commons.topic.domain.TopicConfig;
import com.icthh.xm.commons.topic.service.DynamicConsumerConfiguration;
import com.icthh.xm.commons.topic.service.dto.RefreshDynamicConsumersEvent;
import com.icthh.xm.tmf.ms.activation.config.ApplicationProperties;
import com.icthh.xm.tmf.ms.activation.events.MessageEventHandlerFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.lang3.StringUtils.upperCase;

@Slf4j
public class ActivationDynamicTopicConsumerConfiguration implements DynamicConsumerConfiguration {

    public static final String SAGA_EVENTS_PREFIX = "saga-events-";

    private static final String AUTO_OFFSET_RESET_EARLIEST = "earliest";
    private static final Integer DEFAULT_KAFKA_CONCURRENCY_COUNT = 4;

    @Value("${spring.application.name}")
    private String appName;

    private final Map<String, List<DynamicConsumer>> dynamicConsumersByTenant;
    private final MessageEventHandlerFacade messageEventHandlerFacade;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ApplicationProperties applicationProperties;

    public ActivationDynamicTopicConsumerConfiguration(ApplicationEventPublisher applicationEventPublisher,
                                                       EventHandler eventHandler,
                                                       ObjectMapper objectMapper,
                                                       ApplicationProperties applicationProperties) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.messageEventHandlerFacade = new MessageEventHandlerFacade(eventHandler, objectMapper);
        this.dynamicConsumersByTenant = new ConcurrentHashMap<>();
        this.applicationProperties = applicationProperties;
    }

    @Override
    public List<DynamicConsumer> getDynamicConsumers(String tenantKey) {
        return dynamicConsumersByTenant.getOrDefault(getTenantMapKey(tenantKey), new ArrayList<>());
    }

    public void sendRefreshDynamicConsumersEvent(String tenantKey) {
        applicationEventPublisher.publishEvent(new RefreshDynamicConsumersEvent(this, tenantKey));
    }

    public void buildDynamicConsumers(String tenantName) {
        try {
            String tenantKey = upperCase(tenantName);
            createDynamicConsumer(buildChanelName(tenantKey), appName, tenantKey, AUTO_OFFSET_RESET_EARLIEST);

        } catch (Exception e) {
            log.error("Error create channels for tenant {}", tenantName, e);
            throw e;
        }
    }

    private void createDynamicConsumer(String chanelName, String consumerGroup, String tenantName, String startOffset) {
        DynamicConsumer dynamicConsumer = new DynamicConsumer();
        dynamicConsumer.setConfig(buildTopicConfig(chanelName, consumerGroup, startOffset));
        dynamicConsumer.setMessageHandler(messageEventHandlerFacade);

        String tenantMapKey = getTenantMapKey(tenantName);
        dynamicConsumersByTenant.computeIfAbsent(tenantMapKey, v -> new ArrayList<>()).add(dynamicConsumer);
    }

    protected TopicConfig buildTopicConfig(String chanelName, String consumerGroup, String startOffset) {
        TopicConfig topicConfig = new TopicConfig();
        topicConfig.setKey(chanelName);
        topicConfig.setTypeKey(chanelName);
        topicConfig.setTopicName(chanelName);
        topicConfig.setRetriesCount(Integer.MAX_VALUE);
        topicConfig.setGroupId(consumerGroup);
        topicConfig.setAutoOffsetReset(startOffset);

        int kafkaConcurrencyCount = applicationProperties.getKafkaConcurrencyCount();
        if (kafkaConcurrencyCount <= 0) {
            log.warn("buildTopicConfig: concurrency setting is less than 1, using default: {}",
                DEFAULT_KAFKA_CONCURRENCY_COUNT);
            kafkaConcurrencyCount = DEFAULT_KAFKA_CONCURRENCY_COUNT;
        }
        topicConfig.setConcurrency(kafkaConcurrencyCount);
        return topicConfig;
    }

    private String getTenantMapKey(String tenantName) {
        return tenantName != null ? tenantName.toLowerCase() : null;
    }

    public static String buildChanelName(String tenantKey) {
        return SAGA_EVENTS_PREFIX + tenantKey.toUpperCase();
    }
}
