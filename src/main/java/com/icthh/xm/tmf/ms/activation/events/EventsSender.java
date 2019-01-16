package com.icthh.xm.tmf.ms.activation.events;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;

public interface EventsSender {
    void sendEvent(SagaEvent sagaEvent);
    void resendEvent(SagaEvent sagaEvent);
}
