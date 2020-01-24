package com.icthh.xm.tmf.ms.activation.config;

import static org.springframework.kafka.support.KafkaHeaders.MESSAGE_KEY;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.binder.PartitionKeyExtractorStrategy;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBinderConfigurationProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties;
import org.springframework.cloud.stream.binding.BinderAwareChannelResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class KafkaPartitionConfiguration {

    public static final String PARTITION_KEY = "partitionKey";
    private static final String TASKS_PARTITION_KEY_EXTRACTOR_STRATEGY = "tasksPartitionKeyExtractorStrategy";

    private final KafkaBinderConfigurationProperties properties;

    @Bean
    public BinderAwareChannelResolver.NewDestinationBindingCallback<KafkaProducerProperties> dynamicBindingConfigurer() {
        return ((channelName, channel, producerProperties, extendedProducerProperties) -> {
            producerProperties.setPartitionCount(properties.getMinPartitionCount());
            producerProperties.setPartitionKeyExtractorName(TASKS_PARTITION_KEY_EXTRACTOR_STRATEGY);
        });
    }

    @Bean
    public PartitionKeyExtractorStrategy tasksPartitionKeyExtractorStrategy() {
        return (message) -> message.getHeaders().getOrDefault(PARTITION_KEY, message.getHeaders().get(MESSAGE_KEY));
    }

}
