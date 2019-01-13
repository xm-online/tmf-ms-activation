package com.icthh.xm.tmf.ms.activation.events;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;

public interface EventsManager {
    void sendEvent(SagaEvent sagaEvent);
}
