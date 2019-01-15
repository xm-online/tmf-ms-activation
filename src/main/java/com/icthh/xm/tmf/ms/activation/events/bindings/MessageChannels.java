package com.icthh.xm.tmf.ms.activation.events.bindings;

import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;

public interface MessageChannels {

        String ACTIVATION_EVENTS = "activation-events";

        @Input(ACTIVATION_EVENTS + "-in")
        SubscribableChannel receive();

        @Output(ACTIVATION_EVENTS + "-out")
        MessageChannel outbound();

}
