package com.icthh.xm.tmf.ms.activation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OnTxFinishHandler implements ApplicationListener<OnTxFinishEvent> {

    private final SagaService sagaService;

    @Override
    public void onApplicationEvent(OnTxFinishEvent onTxFinishEvent) {
        String parentEventId = onTxFinishEvent.getTransaction().getParentEventId();
        if (StringUtils.isNotBlank(parentEventId)) {
            log.info("Resume parent transaction {} with context {}", parentEventId, onTxFinishEvent.getLastTaskContext());
            sagaService.continueTask(parentEventId, onTxFinishEvent.getLastTaskContext());
        }
    }
}
