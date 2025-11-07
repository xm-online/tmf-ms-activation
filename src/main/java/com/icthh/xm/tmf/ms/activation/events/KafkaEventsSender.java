package com.icthh.xm.tmf.ms.activation.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.commons.exceptions.BusinessException;
import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.commons.lep.spring.LepService;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.utils.LazyObjectProvider;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
@Component
@RequiredArgsConstructor
@LepService(group = "service.kafka")
public class KafkaEventsSender implements EventsSender {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> template;
    private final QueueNameResolver queueNameResolver;

    private final LazyObjectProvider<KafkaEventsSender> selfProvider;

    @Retryable(include = BusinessException.class,
               maxAttemptsExpression = "${application.kafkaEventSender.retry.max-attempts}",
               backoff = @Backoff(delayExpression = "${application.kafkaEventSender.retry.delay}",
               multiplierExpression = "${application.kafkaEventSender.retry.multiplier}"))
    @Override
    public void sendEvent(SagaEvent sagaEvent) {
        String queueName = queueNameResolver.resolveQueueName(sagaEvent);
        Integer partitionKey = getPartitionKeyForTopic(queueName, sagaEvent);
        String payload = getMessagePayload(sagaEvent);

        template.send(queueName, partitionKey, sagaEvent.getId(), payload);

        log.info("Saga event successfully sent: {}", sagaEvent);
    }

    private String getMessagePayload(SagaEvent sagaEvent) {
        try {
            String sagaEventJson = objectMapper.writeValueAsString(sagaEvent);
            return Base64.getEncoder().encodeToString(sagaEventJson.getBytes(UTF_8));

        } catch (JsonProcessingException e) {
            log.warn("Cannot convert message to json: {}", sagaEvent, e);
            throw new BusinessException("Cannot convert message to json: " + sagaEvent);
        }
    }

    public Integer getPartitionKeyForTopic(String topic, SagaEvent sagaEvent) {
        int partitionCount = Math.max(template.partitionsFor(topic).size(), 1);
        return Math.abs(selfProvider.get().getPartitionKey(sagaEvent).hashCode()) % partitionCount;
    }

    @LogicExtensionPoint("GetPartitionKey")
    public String getPartitionKey(SagaEvent sagaEvent) {
        return sagaEvent.getTransactionId();
    }

    @Override
    @SneakyThrows
    public void resendEvent(SagaEvent sagaEvent) {
        sendEvent(sagaEvent);
    }
}