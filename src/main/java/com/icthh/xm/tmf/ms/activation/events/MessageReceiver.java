package com.icthh.xm.tmf.ms.activation.events;

import static com.icthh.xm.tmf.ms.activation.events.bindings.MessageChannels.ACTIVATION_EVENTS;
import static org.springframework.kafka.support.KafkaHeaders.ACKNOWLEDGMENT;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.service.SagaService;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageReceiver {

    private final SagaService sagaService;

    @StreamListener(ACTIVATION_EVENTS + "-in")
    public void receiveSagaEvent(@Payload SagaEvent event, @Header(ACKNOWLEDGMENT) Acknowledgment acknowledgment) {
        sagaService.onSagaEvent(event);
        acknowledgment.acknowledge();
    }

}
