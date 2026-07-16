package com.icthh.xm.tmf.ms.activation.config;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import com.icthh.xm.commons.config.client.repository.TenantListRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Slf4j
@RequiredArgsConstructor
@Component
public class ActivationKafkaOffsetsMetric {

    private final String METRIC_NAME = "kafka.offsets.";
    private final String TOPIC_PREFIX = "saga-events-";

    @Value("${spring.kafka.consumer.group-id}")
    private String group;

    @Value("${application.kafkaOffsetCacheTTL:30}")
    private Long cacheTTL;

    private final TenantListRepository tenantListRepository;
    private final KafkaProperties kafkaProperties;
    private final ApplicationProperties applicationProperties;
    private final MeterRegistry meterRegistry;

    private LoadingCache<String, Offsets> offsetsCache;

    private volatile Admin admin;

    @Getter
    @RequiredArgsConstructor
    private static class Offsets {

        private final long totalLag;
        private final long totalCurrentOffset;
        private final long totalEndOffset;
    }

    @PostConstruct
    public void init() {
        offsetsCache = CacheBuilder.newBuilder()
                .expireAfterWrite(cacheTTL, TimeUnit.SECONDS)
                .build(new CacheLoader<>() {
                    @Override
                    public Offsets load(String topic) {
                        return calculateConsumerOffsetsOnTopic(topic, group);
                    }
                });

        tenantListRepository.getTenants().forEach(this::registerTenantMetrics);
    }

    @PreDestroy
    public void destroy() {
        if (admin != null) {
            admin.close();
        }
    }

    private Offsets calculateConsumerOffsetsOnTopic(String topic, String group) {
        long timeout = applicationProperties.getKafkaOffsetsMetricTimeout();
        try {
            Admin adminClient = getAdmin();

            TopicDescription description = adminClient.describeTopics(List.of(topic))
                    .allTopicNames().get(timeout, TimeUnit.SECONDS).get(topic);
            if (description == null) {
                return new Offsets(0, 0, 0);
            }

            List<TopicPartition> topicPartitions = description.partitions().stream()
                    .map(partition -> new TopicPartition(topic, partition.partition()))
                    .toList();

            Map<TopicPartition, OffsetSpec> latestOffsetSpecs = topicPartitions.stream()
                    .collect(Collectors.toMap(topicPartition -> topicPartition, topicPartition -> OffsetSpec.latest()));

            Map<TopicPartition, ListOffsetsResultInfo> endOffsets =
                    adminClient.listOffsets(latestOffsetSpecs).all().get(timeout, TimeUnit.SECONDS);

            Map<TopicPartition, OffsetAndMetadata> committedOffsets =
                    adminClient.listConsumerGroupOffsets(group)
                            .partitionsToOffsetAndMetadata().get(timeout, TimeUnit.SECONDS);

            long totalEndOffset = 0;
            long totalCurrentOffset = 0;
            for (TopicPartition topicPartition : topicPartitions) {
                ListOffsetsResultInfo endOffset = endOffsets.get(topicPartition);
                if (endOffset != null) {
                    totalEndOffset += endOffset.offset();
                }
                OffsetAndMetadata committedOffset = committedOffsets.get(topicPartition);
                if (committedOffset != null) {
                    totalCurrentOffset += committedOffset.offset();
                }
            }

            return new Offsets(totalEndOffset - totalCurrentOffset, totalCurrentOffset, totalEndOffset);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Cannot generate metric for topic: {}", topic, e);
            return new Offsets(Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);
        } catch (Exception e) {
            log.warn("Cannot generate metric for topic: {}", topic, e);
            return new Offsets(Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);
        }
    }

    private Admin getAdmin() {
        if (admin == null) {
            synchronized (this) {
                if (admin == null) {
                    admin = createAdmin();
                }
            }
        }
        return admin;
    }

    private Admin createAdmin() {
        Map<String, Object> props = new HashMap<>();
        Map<String, Object> adminProps = kafkaProperties.buildAdminProperties();
        if (!ObjectUtils.isEmpty(adminProps)) {
            props.putAll(adminProps);
        }
        if (!props.containsKey(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG)) {
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        }
        return Admin.create(props);
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
