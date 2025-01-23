package com.icthh.xm.tmf.ms.activation.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icthh.xm.commons.exceptions.BusinessException;
import com.icthh.xm.commons.lep.spring.LepService;
import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@LepService(group = "service.kafka")
public class KafkaEventsSender implements EventsSender {

    public static final String PARTITION_KEY = "partitionKey";

    private final ObjectMapper kafkaEventSendObjectMapper = initObjectMapper();
    private final KafkaTemplate<String, String> template;
    private final QueueNameResolver queueNameResolver;

    @Setter(onMethod = @__(@Autowired))
    private KafkaEventsSender self;

    @Retryable(include = BusinessException.class,
               maxAttemptsExpression = "${application.kafkaEventSender.retry.max-attempts}",
               backoff = @Backoff(delayExpression = "${application.kafkaEventSender.retry.delay}",
               multiplierExpression = "${application.kafkaEventSender.retry.multiplier}"))
    @Override
    public void sendEvent(SagaEvent sagaEvent) {
        try {
            String queueName = queueNameResolver.resolveQueueName(sagaEvent);
            Message<SagaEvent> message = MessageBuilder.withPayload(sagaEvent)
                .setHeader(KafkaHeaders.MESSAGE_KEY, sagaEvent.getId())
                .setHeader(PARTITION_KEY, self.getPartitionKey(sagaEvent))
                .build();

            template.send(queueName, kafkaEventSendObjectMapper.writeValueAsString(message));
            log.info("Saga event successfully sent: {}", sagaEvent);

        } catch (JsonProcessingException e) {
            log.warn("Cannot send saga event: {}", sagaEvent);
            throw new BusinessException("Cannot send saga event: " + sagaEvent);
        }
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

    private ObjectMapper initObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}