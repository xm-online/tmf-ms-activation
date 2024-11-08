package com.icthh.xm.tmf.ms.activation.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OnTxFinishHandler implements ApplicationListener<OnTxFinishEvent> {

    private final SagaService sagaService;

    @Override
    public void onApplicationEvent(OnTxFinishEvent onTxFinishEvent) {
        String parentEventId = onTxFinishEvent.getTransaction().getParentEventId();
        if (StringUtils.isNotBlank(parentEventId)) {
            sagaService.continueTask(parentEventId, onTxFinishEvent.getLastTaskContext());
        }
    }
}
