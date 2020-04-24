package com.icthh.xm.tmf.ms.activation.service;

import static com.google.common.base.Predicates.not;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.ON_RETRY;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.WAIT_CONDITION_TASK;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.WAIT_DEPENDS_TASK;

import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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

    private final Map<String, Boolean> scheduledEventsId = new ConcurrentHashMap<>();

    private RetryService self;

    @PostConstruct
    public void postConstruct() {
        self.rescheduleAllEvents();
    }

    @Transactional
    public void rescheduleAllEvents() {
        sagaEventRepository.findByStatus(ON_RETRY).forEach(this::doResend);
        sagaEventRepository.findByStatus(WAIT_DEPENDS_TASK).forEach(this::doResend);
        sagaEventRepository.findByStatus(WAIT_CONDITION_TASK).forEach(this::doResend);
    }

    public void retry(SagaEvent sagaEvent, SagaTaskSpec sagaTaskSpec, SagaEvent.SagaEventStatus eventStatus) {
        long backOff = Math.min(sagaTaskSpec.getMaxBackOff(), sagaEvent.getBackOff() + sagaTaskSpec.getBackOff());
        sagaEvent.setBackOff(backOff);
        sagaEvent.setRetryNumber(sagaEvent.getRetryNumber() + 1);

        if (sagaEvent.getRetryNumber() > sagaTaskSpec.getRetryCount() && sagaTaskSpec.getRetryCount() >= 0) {
            log.warn("Limit retry exceeded for event {}. {} > {}", sagaEvent, sagaEvent.getRetryNumber(),
                     sagaTaskSpec.getRetryCount());
            return;
        }

        scheduleRetry(sagaEvent, eventStatus);
    }

    public void retry(SagaEvent sagaEvent, SagaTaskSpec sagaTaskSpec) {
        retry(sagaEvent, sagaTaskSpec, ON_RETRY);
    }

    public void retryForTaskWaitCondition(SagaEvent sagaEvent, SagaTaskSpec sagaTaskSpec) {
        retry(sagaEvent, sagaTaskSpec, WAIT_CONDITION_TASK);
    }

    public void retryForWaitDependsTask(SagaEvent sagaEvent, SagaTaskSpec sagaTaskSpec) {
        retry(sagaEvent, sagaTaskSpec, WAIT_DEPENDS_TASK);
    }

    private void scheduleRetry(SagaEvent sagaEvent, SagaEvent.SagaEventStatus eventStatus) {
        sagaEvent.setStatus(eventStatus);
        SagaEvent savedSagaEvent = sagaEventRepository.save(sagaEvent);

        if (scheduledEventsId.containsKey(sagaEvent.getId())) {
            log.warn("Event {} already scheduled", sagaEvent);
            return;
        }

        log.info("Schedule event {} for delay {}", savedSagaEvent, sagaEvent.getBackOff());
        scheduledEventsId.put(sagaEvent.getId(), true);
        threadPoolTaskScheduler
            .schedule(() -> doResend(savedSagaEvent), Instant.now().plusSeconds(sagaEvent.getBackOff()));
    }

    public void doResend(SagaEvent sagaEvent) {
        scheduledEventsId.remove(sagaEvent.getId());
        tenantUtils.doInTenantContext(() -> {
            try {
                log.info("Retry event {}. Send into broker.", sagaEvent);
                self.removeAndSend(sagaEvent);
            } catch (Exception e) {
                log.info("Error has happend.", e);
                scheduleRetry(sagaEvent, ON_RETRY);
                throw e;
            }
        }, sagaEvent.getTenantKey());
    }

    @Transactional
    public void removeAndSend(SagaEvent sagaEvent) {
        Optional<SagaEvent> actualSagaEvent = sagaEventRepository.findById(sagaEvent.getId());
        if (actualSagaEvent.filter(not(SagaEvent::isInQueue)).isPresent()) {
            SagaEvent event = actualSagaEvent.get();
            event.markAsInQueue();
            event = sagaEventRepository.save(event);
            eventsSender.sendEvent(event);
        } else {
            log.warn("Event not found {}", sagaEvent);
        }
    }

    @Autowired
    public void setSelf(RetryService self) {
        this.self = self;
    }

}
