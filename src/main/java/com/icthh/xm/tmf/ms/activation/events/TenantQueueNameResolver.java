package com.icthh.xm.tmf.ms.activation.events;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;

import static com.icthh.xm.tmf.ms.activation.events.bindings.DynamicTopicConsumerConfiguration.buildChanelName;

public class TenantQueueNameResolver implements QueueNameResolver {

    @Override
    public String resolveQueueName(SagaEvent sagaEvent) {
        return buildChanelName(sagaEvent.getTenantKey().toUpperCase());
    }
}
