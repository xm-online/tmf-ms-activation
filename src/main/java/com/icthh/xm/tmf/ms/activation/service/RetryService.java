package com.icthh.xm.tmf.ms.activation.service;

import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventType.ON_RETRY;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import java.time.Instant;
import java.util.List;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryService {

    private final ThreadPoolTaskScheduler threadPoolTaskScheduler;
    private final EventsSender eventsSender;
    private final SagaEventRepository sagaEventRepository;
    private final TenantUtils tenantUtils;

    private RetryService self;

    @PostConstruct
    public void postConstruct() {
        self.resheduleAllEvents();
    }

    @Transactional
    public void resheduleAllEvents() {
        List<SagaEvent> events = sagaEventRepository.findByStatus(ON_RETRY);
        events.forEach(eventsSender::resendEvent);
    }

    public void retry(SagaEvent sagaEvent, SagaTaskSpec sagaTaskSpec) {
        int backOff = Math.min(sagaTaskSpec.getMaxBackOff(), sagaEvent.getBackOff() + sagaTaskSpec.getBackOff());
        sagaEvent.setBackOff(backOff);
        sagaEvent.setRetryNumber(sagaEvent.getRetryNumber() + 1);

        if (sagaEvent.getRetryNumber() > sagaTaskSpec.getRetryCount() && sagaTaskSpec.getRetryCount() >= 0) {
            log.warn("Limit retry exceeded for event {}. {} > {}", sagaEvent, sagaEvent.getRetryNumber(),
                     sagaTaskSpec.getRetryCount());
            return;
        }

        scheduleRetry(sagaEvent);
    }

    private void scheduleRetry(SagaEvent sagaEvent) {
        SagaEvent savedSagaEvent = sagaEventRepository.save(sagaEvent);
        log.info("Schedule event {} for delay {}", savedSagaEvent, sagaEvent.getBackOff());
        threadPoolTaskScheduler
            .schedule(() -> doResend(savedSagaEvent), Instant.now().plusSeconds(sagaEvent.getBackOff()));
    }

    public void doResend(SagaEvent sagaEvent) {
        tenantUtils.doInTenantContext(() -> {
            try {
                log.info("Retry event {}. Send into broker.", sagaEvent);
                eventsSender.sendEvent(sagaEvent);
                sagaEventRepository.delete(sagaEvent);
            } catch (Exception e) {
                scheduleRetry(sagaEvent);
                log.info("Error has happend.", e);
                throw e;
            }
        }, sagaEvent.getTenantKey());
    }

    @Autowired
    public void setSelf(RetryService self) {
        this.self = self;
    }

}
