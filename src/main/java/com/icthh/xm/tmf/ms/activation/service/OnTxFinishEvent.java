package com.icthh.xm.tmf.ms.activation.service;

import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import java.util.Map;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OnTxFinishEvent extends ApplicationEvent {

    private final SagaTransaction transaction;
    private final Map<String, Object> lastTaskContext;

    public OnTxFinishEvent(Object source, SagaTransaction transaction, Map<String, Object> lastTaskContext) {
        super(source);
        this.transaction = transaction;
        this.lastTaskContext = lastTaskContext;
    }
}
