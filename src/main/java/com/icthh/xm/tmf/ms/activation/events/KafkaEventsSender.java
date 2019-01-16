package com.icthh.xm.tmf.ms.activation.events;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.events.bindings.MessaginsConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.binding.BinderAwareChannelResolver;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventsSender implements EventsSender {

    private final BinderAwareChannelResolver channelResolver;

    @Override
    public void sendEvent(SagaEvent sagaEvent) {
        log.info("Send saga event: {}", sagaEvent);
        channelResolver.resolveDestination(MessaginsConfiguration.buildChanelName(sagaEvent.getTenantKey().toUpperCase()))
            .send(MessageBuilder.withPayload(sagaEvent).build());
    }

    @Override
    public void resendEvent(SagaEvent sagaEvent) {

    }


}
