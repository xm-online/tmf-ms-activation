package com.icthh.xm.tmf.ms.activation.config;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import com.icthh.xm.commons.config.client.repository.TenantListRepository;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
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
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Slf4j
@RequiredArgsConstructor
@Component
public class ActivationKafkaOffsetsMetric {

    private final String METRIC_NAME = "kafka.offsets.";
    private final String TOPIC_PREFIX = "saga-events-";
    private final Long CACHE_TTL = 10L;

    @Value("${spring.kafka.consumer.group-id}")
    private String group;

    private final TenantListRepository tenantListRepository;
    private final KafkaProperties kafkaProperties;
    private final ApplicationProperties applicationProperties;
    private final MeterRegistry meterRegistry;

    private final LoadingCache<String, Offsets> offsetsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(CACHE_TTL, TimeUnit.SECONDS)
            .build(new CacheLoader<>() {
                @Override
                public Offsets load(String topic) {
                    return calculateConsumerOffsetsOnTopic(topic, group);
                }
            });

    private ConsumerFactory<?, ?> defaultConsumerFactory;
    private Consumer<?, ?> consumer;


    @Getter
    @RequiredArgsConstructor
    private static class Offsets {

        private final long totalLag;
        private final long totalCurrentOffset;
        private final long totalEndOffset;
    }

    @PostConstruct
    public void registerMetrics() {
        tenantListRepository.getTenants().forEach(this::registerTenantMetrics);
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
                        Map<TopicPartition, OffsetAndMetadata> current = consumer.committed(Set.of(endOffset.getKey()));
                        if (current != null) {
                            totalEndOffset += endOffset.getValue();
                            OffsetAndMetadata offsetAndMetadata = current.get(endOffset.getKey());
                            if (offsetAndMetadata != null) {
                                totalCurrentOffset += offsetAndMetadata.offset();
                            }
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
            if (!ObjectUtils.isEmpty(kafkaProperties.buildConsumerProperties())) {
                props.putAll(kafkaProperties.buildConsumerProperties());
            }
            if (!props.containsKey(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)) {
                props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                    this.kafkaProperties.getBootstrapServers());
            }
            props.put("group.id", group);
            this.defaultConsumerFactory = new DefaultKafkaConsumerFactory<>(props);
        }

        return this.defaultConsumerFactory;
    }

    private void registerTenantMetrics(String tenantName) {
        String topic = TOPIC_PREFIX + tenantName.toUpperCase();
        registerGauge("lag", topic, tenantName,
                () -> toDouble(getOffsets(topic).getTotalLag()));
        registerGauge("current", topic, tenantName,
                () -> toDouble(getOffsets(topic).getTotalCurrentOffset()));
        registerGauge("end", topic, tenantName,
                () -> toDouble(getOffsets(topic).getTotalEndOffset()));
    }

    private Offsets getOffsets(String topic) {
        try {
            return offsetsCache.get(topic);
        } catch (Exception e) {
            log.warn("Kafka offsets load failed for topic {}", topic, e);
            return new Offsets(Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);
        }
    }

    private void registerGauge(
            String metricSuffix,
            String topic,
            String tenantName,
            Supplier<Double> supplier
    ) {
        Gauge.builder(METRIC_NAME + metricSuffix, supplier)
                .tag("topic", topic)
                .tag("tenant", tenantName)
                .register(meterRegistry);
    }

    private Double toDouble(Object value) {
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        return Double.NaN;
    }
}
