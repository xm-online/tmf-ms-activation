package com.icthh.xm.tmf.ms.activation.config;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.icthh.xm.commons.config.client.repository.TenantListRepository;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBinderConfigurationProperties;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Slf4j
@RequiredArgsConstructor
@Component
public class ActivationKafkaOffsetsMetric implements MetricSet {

    private final String METRIC_NAME = "kafka.offsets.";
    private final String TOPIC_PREFIX = "saga-events-";

    @Value("${spring.kafka.consumer.group-id}")
    private String group;

    private final TenantListRepository tenantListRepository;
    private final KafkaBinderConfigurationProperties binderConfigurationProperties;
    private final ApplicationProperties applicationProperties;

    private ConsumerFactory<?, ?> defaultConsumerFactory;
    private Consumer<?, ?> consumer;

    @Getter
    @RequiredArgsConstructor
    private class Offsets {

        private final long totalLag;
        private final long totalCurrentOffset;
        private final long totalEndOffset;
    }

    private Offsets calculateConsumerOffsetsOnTopic(String topic, String group) {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<Offsets> future = exec.submit(() -> {

            long totalCurrentOffset = 0;
            long totalEndOffset = 0;

            try {
                if (consumer == null) {
                    synchronized (ActivationKafkaOffsetsMetric.this) {
                        if (consumer == null) {
                            consumer = createConsumerFactory(group).createConsumer();
                        }
                    }
                }
                synchronized (consumer) {
                    List<PartitionInfo> partitionInfos = consumer.partitionsFor(topic);
                    List<TopicPartition> topicPartitions = new LinkedList<>();
                    for (PartitionInfo partitionInfo : partitionInfos) {
                        topicPartitions.add(new TopicPartition(partitionInfo.topic(), partitionInfo.partition()));
                    }

                    Map<TopicPartition, Long> endOffsets = consumer.endOffsets(topicPartitions);

                    for (Map.Entry<TopicPartition, Long> endOffset : endOffsets.entrySet()) {
                        OffsetAndMetadata current = consumer.committed(endOffset.getKey());
                        if (current != null) {
                            totalEndOffset += endOffset.getValue();
                            totalCurrentOffset += current.offset();
                        } else {
                            totalEndOffset += endOffset.getValue();
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Cannot generate metric for topic: " + topic, e);
            }

            return new Offsets(totalEndOffset - totalCurrentOffset, totalCurrentOffset, totalEndOffset);
        });
        try {
            return future.get(applicationProperties.getKafkaOffsetsMetricTimeout(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Offsets(Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);
        } catch (ExecutionException | TimeoutException e) {
            return new Offsets(Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);
        } finally {
            exec.shutdownNow();
        }
    }

    private ConsumerFactory<?, ?> createConsumerFactory(String group) {
        if (this.defaultConsumerFactory == null) {
            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
            if (!ObjectUtils.isEmpty(binderConfigurationProperties.mergedConsumerConfiguration())) {
                props.putAll(binderConfigurationProperties.mergedConsumerConfiguration());
            }
            if (!props.containsKey(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)) {
                props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                    this.binderConfigurationProperties.getKafkaConnectionString());
            }
            props.put("group.id", group);
            this.defaultConsumerFactory = new DefaultKafkaConsumerFactory<>(props);
        }

        return this.defaultConsumerFactory;
    }

    @Override
    public Map<String, Metric> getMetrics() {
        Map<String, Metric> metrics = new HashMap<>();
        tenantListRepository.getTenants().forEach(tenantName -> {
            String topic = TOPIC_PREFIX + tenantName.toUpperCase();
            metrics.put(METRIC_NAME + topic, (Gauge<Offsets>) () -> calculateConsumerOffsetsOnTopic(topic, group));
        });

        return metrics;
    }
}
