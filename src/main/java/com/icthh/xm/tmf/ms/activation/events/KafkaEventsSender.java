package com.icthh.xm.tmf.ms.activation.events;

import static com.icthh.xm.tmf.ms.activation.config.KafkaPartitionConfiguration.PARTITION_KEY;

import com.icthh.xm.commons.exceptions.BusinessException;
import com.icthh.xm.commons.lep.spring.LepService;
import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.events.bindings.MessagingConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.binding.BinderAwareChannelResolver;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@LepService(group = "service.kafka")
public class KafkaEventsSender implements EventsSender {

    private final BinderAwareChannelResolver channelResolver;

    @Setter(onMethod = @__(@Autowired))
    private KafkaEventsSender self;

    @Retryable(include = BusinessException.class,
               maxAttemptsExpression = "${application.kafkaEventSender.retry.max-attempts}",
               backoff = @Backoff(delayExpression = "${application.kafkaEventSender.retry.delay}",
               multiplierExpression = "${application.kafkaEventSender.retry.multiplier}"))
    @Override
    public void sendEvent(SagaEvent sagaEvent) {
        boolean result = channelResolver
            .resolveDestination(MessagingConfiguration.buildChanelName(sagaEvent.getTenantKey().toUpperCase()))
            .send(MessageBuilder.withPayload(sagaEvent)
                                .setHeader(KafkaHeaders.MESSAGE_KEY, sagaEvent.getId())
                                .setHeader(PARTITION_KEY, self.getPartitionKey(sagaEvent))
                                .build());

        if (!result) {
            log.warn("Cannot send saga event: {}", sagaEvent);
            throw new BusinessException("Cannot send saga event: " + sagaEvent);
        }
        log.info("Saga event successfully sent: {}", sagaEvent);
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