package com.icthh.xm.tmf.ms.activation.config;

import static com.icthh.xm.commons.config.client.repository.TenantListRepository.TENANTS_LIST_CONFIG_KEY;
import static java.util.Arrays.asList;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.icthh.xm.commons.config.client.repository.CommonConfigRepository;
import com.icthh.xm.commons.config.client.repository.TenantListRepository;
import com.icthh.xm.commons.config.domain.Configuration;
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
import lombok.Setter;
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
public class KafkaOffsetsMetric implements MetricSet {

    private final String METRIC_NAME = "kafka.offsets.";
    private final String TOPIC_PREFIX = "saga-events-";

    @Setter
    private int timeout = 10;

    @Value("${spring.kafka.consumer.group-id}")
    private String group;

    private final CommonConfigRepository commonConfigRepo;
    private final TenantListRepository tenantListRepository;
    private final KafkaBinderConfigurationProperties binderConfigurationProperties;

    private ConsumerFactory<?, ?> defaultConsumerFactory;
    private Consumer<?, ?> consumer;

    @Getter
    @RequiredArgsConstructor
    private class Offsets {

        private final long totalLag;
        private final long totalCurrentOffset;
        private final long totalEndOffset;
    }

    private Offsets calculateConsumerLagOnTopic(String topic, String group) {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<Offsets> future = exec.submit(() -> {

            long totalCurrentOffset = 0;
            long totalEndOffset = 0;

            try {
                if (consumer == null) {
                    synchronized (KafkaOffsetsMetric.this) {
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
            return future.get(this.timeout, TimeUnit.SECONDS);
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
            if (!ObjectUtils.isEmpty(binderConfigurationProperties.getConsumerConfiguration())) {
                props.putAll(binderConfigurationProperties.getConsumerConfiguration());
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
        Map<String, Metric> stringMetricMap = new HashMap<>();
        Map<String, Configuration> config = commonConfigRepo.getConfig(null, asList(TENANTS_LIST_CONFIG_KEY));
        tenantListRepository.onInit(TENANTS_LIST_CONFIG_KEY, config.get(TENANTS_LIST_CONFIG_KEY).getContent());

        tenantListRepository.getTenants().forEach(tenantName -> {
            String topic = TOPIC_PREFIX + tenantName.toUpperCase();
            stringMetricMap.put(METRIC_NAME + topic, (Gauge<Offsets>) () -> calculateConsumerLagOnTopic(topic, group));
        });

        return stringMetricMap;
    }
}
