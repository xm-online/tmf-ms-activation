package com.icthh.xm.tmf.ms.activation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.tmf.ms.activation.events.bindings.ActivationDynamicTopicConsumerConfiguration;
import com.icthh.xm.tmf.ms.activation.events.bindings.EventHandler;
import com.icthh.xm.tmf.ms.activation.events.bindings.MessagingConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DynamicTopicMessagingConfiguration {

    @Bean
    @ConditionalOnMissingBean(ActivationDynamicTopicConsumerConfiguration.class)
    public ActivationDynamicTopicConsumerConfiguration activationDynamicTopicConsumerConfiguration(ApplicationEventPublisher applicationEventPublisher,
                                                                                         EventHandler eventHandler,
                                                                                         ObjectMapper objectMapper) {
        return new ActivationDynamicTopicConsumerConfiguration(applicationEventPublisher, eventHandler, objectMapper);
    }

    @Bean
    public MessagingConfiguration dynamicTopicChannelManager(ActivationDynamicTopicConsumerConfiguration activationDynamicTopicConsumerConfiguration,
                                                             ObjectMapper objectMapper) {
        return new MessagingConfiguration(activationDynamicTopicConsumerConfiguration, objectMapper);
    }
}
