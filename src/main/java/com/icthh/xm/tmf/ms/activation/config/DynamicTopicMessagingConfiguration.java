package com.icthh.xm.tmf.ms.activation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.tmf.ms.activation.events.bindings.DynamicTopicConsumerConfiguration;
import com.icthh.xm.tmf.ms.activation.events.bindings.EventHandler;
import com.icthh.xm.tmf.ms.activation.events.bindings.MessagingConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DynamicTopicMessagingConfiguration {

    @Bean
    public DynamicTopicConsumerConfiguration dynamicTopicConsumerConfiguration(ApplicationEventPublisher applicationEventPublisher,
                                                                               EventHandler eventHandler) {
        return new DynamicTopicConsumerConfiguration(applicationEventPublisher, eventHandler);
    }

    @Bean
    public MessagingConfiguration schedulerChannelManager(DynamicTopicConsumerConfiguration dynamicTopicConsumerConfiguration,
                                                          ObjectMapper objectMapper) {
        return new MessagingConfiguration(dynamicTopicConsumerConfiguration, objectMapper);
    }
}
