package com.icthh.xm.tmf.ms.activation.events;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;

public interface QueueNameResolver {
    String resolveQueueName(String transactionTypeKey, SagaEvent sagaEvent);
    String resolveQueueName(SagaEvent sagaEvent);
}
