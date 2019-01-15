package com.icthh.xm.tmf.ms.activation.events.bindings;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

@Component
@RequiredArgsConstructor
public class KafkaEventSender implements EventsSender {

    private final MessageChannels messageChannels;

    @Override
    public void sendEvent(SagaEvent sagaEvent) {
        MessageChannel messageChannel = messageChannels.outbound();
        messageChannel.send(MessageBuilder
            .withPayload(sagaEvent)
            .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
            .build());
    }
}
