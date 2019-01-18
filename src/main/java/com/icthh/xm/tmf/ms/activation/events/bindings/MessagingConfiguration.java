package com.icthh.xm.tmf.ms.activation.events.bindings;

import static com.icthh.xm.commons.config.client.repository.TenantListRepository.TENANTS_LIST_CONFIG_KEY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.unwrap;
import static org.apache.commons.lang3.StringUtils.upperCase;
import static org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties.StartOffset.earliest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.commons.config.client.api.RefreshableConfiguration;
import com.icthh.xm.commons.config.domain.TenantState;
import com.icthh.xm.commons.logging.util.MdcUtils;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.binder.ConsumerProperties;
import org.springframework.cloud.stream.binder.HeaderMode;
import org.springframework.cloud.stream.binder.kafka.KafkaMessageChannelBinder;
import org.springframework.cloud.stream.binder.kafka.config.KafkaBinderConfiguration;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBindingProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties.StartOffset;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaExtendedBindingProperties;
import org.springframework.cloud.stream.binding.BindingService;
import org.springframework.cloud.stream.binding.SubscribableChannelBindingTargetFactory;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.context.annotation.Import;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableBinding
@EnableIntegration
@RequiredArgsConstructor
@Import(KafkaBinderConfiguration.class)
public class MessagingConfiguration implements RefreshableConfiguration {

    public static final String SAGA_EVENTS_PREFIX = "saga-events-";
    private final BindingServiceProperties bindingServiceProperties;
    private final SubscribableChannelBindingTargetFactory bindingTargetFactory;
    private final BindingService bindingService;
    private final KafkaExtendedBindingProperties kafkaExtendedBindingProperties = new KafkaExtendedBindingProperties();
    private final Map<String, SubscribableChannel> channels = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final EventHandler eventHandler;

    @Value("${spring.application.name}")
    private String appName;

    @Autowired
    public MessagingConfiguration(BindingServiceProperties bindingServiceProperties,
                                  SubscribableChannelBindingTargetFactory bindingTargetFactory,
                                  BindingService bindingService, KafkaMessageChannelBinder kafkaMessageChannelBinder,
                                  ObjectMapper objectMapper, EventHandler eventHandler) {
        this.bindingServiceProperties = bindingServiceProperties;
        this.bindingTargetFactory = bindingTargetFactory;
        this.bindingService = bindingService;
        this.eventHandler = eventHandler;
        this.objectMapper = objectMapper;
        kafkaMessageChannelBinder.setExtendedBindingProperties(kafkaExtendedBindingProperties);
    }

    public static String buildChanelName(String tenantKey) {
        return SAGA_EVENTS_PREFIX + tenantKey;
    }

    private void createChannels(String tenantName) {
        try {
            String tenantKey = upperCase(tenantName);
            createHandler(buildChanelName(tenantKey), "activation", tenantKey, earliest);
        } catch (Exception e) {
            log.error("Error create channels for tenant " + tenantName, e);
            throw e;
        }
    }

    private synchronized void createHandler(String chanelName, String consumerGroup, String tenantName,
                                            StartOffset startOffset) {
        if (!channels.containsKey(chanelName)) {

            log.info("Create binding to {}. Consumer group {}", chanelName, consumerGroup);

            KafkaBindingProperties props = new KafkaBindingProperties();
            props.getConsumer().setAutoCommitOffset(false);
            props.getConsumer().setStartOffset(startOffset);
            kafkaExtendedBindingProperties.getBindings().put(chanelName, props);

            ConsumerProperties consumerProperties = new ConsumerProperties();
            consumerProperties.setMaxAttempts(Integer.MAX_VALUE);
            consumerProperties.setHeaderMode(HeaderMode.none);
            consumerProperties.setPartitioned(true);

            BindingProperties bindingProperties = new BindingProperties();
            bindingProperties.setConsumer(consumerProperties);
            bindingProperties.setDestination(chanelName);
            bindingProperties.setGroup(consumerGroup);
            bindingServiceProperties.getBindings().put(chanelName, bindingProperties);

            SubscribableChannel channel = bindingTargetFactory.createInput(chanelName);
            bindingService.bindConsumer(channel, chanelName);

            channels.put(chanelName, channel);

            channel.subscribe(message -> {
                try {
                    MdcUtils.putRid(MdcUtils.generateRid() + ":" + tenantName);
                    handleEvent(tenantName, message);
                } catch (Exception e) {
                    log.error("error processign event for tenant [{}]", tenantName, e);
                    throw e;
                } finally {
                    MdcUtils.removeRid();
                }
            });
        }
    }

    private void handleEvent(String tenantName, Message<?> message) {
        final StopWatch stopWatch = StopWatch.createStarted();
        String payloadString = (String) message.getPayload();
        payloadString = unwrap(payloadString, "\"");
        log.info("start processign message for tenant: [{}], base64 body = {}", tenantName, payloadString);
        String eventBody = new String(Base64.getDecoder().decode(payloadString), UTF_8);
        log.info("start processign message for tenant: [{}], json body = {}", tenantName, eventBody);

        eventHandler.onEvent(mapToEvent(eventBody), tenantName);

        message.getHeaders().get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class).acknowledge();
        log.info("stop processign message for tenant: [{}], time = {}", tenantName, stopWatch.getTime());
    }

    @SneakyThrows
    private SagaEvent mapToEvent(String eventBody) {
        return objectMapper.readValue(eventBody, SagaEvent.class);
    }

    @SneakyThrows
    private void updateTenants(String key, String config) {
        log.info("Tenants list was updated");

        if (!TENANTS_LIST_CONFIG_KEY.equals(key)) {
            throw new IllegalArgumentException("Wrong config key to update " + key);
        }

        TypeReference<Map<String, Set<TenantState>>> typeRef = new TypeReference<Map<String, Set<TenantState>>>() {};
        Map<String, Set<TenantState>> tenantsByServiceMap = objectMapper.readValue(config, typeRef);
        Set<TenantState> tenantKeys = tenantsByServiceMap.get(appName);
        tenantKeys.stream().map(TenantState::getName).forEach(this::createChannels);
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
}
