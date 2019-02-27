package com.icthh.xm.tmf.ms.activation.service;

import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventType.ON_RETRY;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventType.WAIT_DEPENDS_TASK;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventType;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
        self.rescheduleAllEvents();
    }

    @Transactional
    public void rescheduleAllEvents() {
        sagaEventRepository.findByStatus(ON_RETRY).forEach(eventsSender::resendEvent);
        sagaEventRepository.findByStatus(WAIT_DEPENDS_TASK).forEach(eventsSender::resendEvent);
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

        scheduleRetry(sagaEvent, ON_RETRY);
    }

    private void scheduleRetry(SagaEvent sagaEvent, SagaEventType onRetry) {
        sagaEvent.setStatus(onRetry);
        SagaEvent savedSagaEvent = sagaEventRepository.save(sagaEvent);
        log.info("Schedule event {} for delay {}", savedSagaEvent, sagaEvent.getBackOff());
        threadPoolTaskScheduler
            .schedule(() -> doResend(savedSagaEvent), Instant.now().plusSeconds(sagaEvent.getBackOff()));
    }

    public void doResend(SagaEvent sagaEvent, SagaEventType onRetry) {
        tenantUtils.doInTenantContext(() -> {
            try {
                log.info("Retry event {}. Send into broker.", sagaEvent);

                Optional<SagaEvent> actualSagaEvent = sagaEventRepository.findById(sagaEvent.getId());
                if (actualSagaEvent.isPresent()) {
                    eventsSender.sendEvent(actualSagaEvent.get());
                    sagaEventRepository.delete(actualSagaEvent.get());
                } else {
                    log.warn("Event not found {}", sagaEvent);
                }
            } catch (Exception e) {
                log.info("Error has happend.", e);
                scheduleRetry(sagaEvent, ON_RETRY);
                throw e;
            }
        }, sagaEvent.getTenantKey());
    }

    @Autowired
    public void setSelf(RetryService self) {
        this.self = self;
    }

    public void retryForWaitDependsTask(SagaEvent sagaEvent, SagaTaskSpec taskSpec) {
        scheduleRetry(sagaEvent, WAIT_DEPENDS_TASK);
    }
}
