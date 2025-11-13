package com.icthh.xm.tmf.ms.activation.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.PartitionInfo;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaTransport {

    private final KafkaTemplate<String, String> template;

    public void send(String topic, Integer partition, String key, String data) {
        template.send(topic, partition, key, data);
    }

    public List<PartitionInfo> partitionsFor(String topic) {
        return template.partitionsFor(topic);
    }
}
