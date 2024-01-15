package com.icthh.xm.tmf.ms.activation.events;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.events.bindings.MessagingConfiguration;

public class TenantQueueNameResolver implements QueueNameResolver {

    @Override
    public String resolveQueueName(SagaEvent sagaEvent) {
        return MessagingConfiguration.buildChanelName(sagaEvent.getTenantKey().toUpperCase());
    }
}
