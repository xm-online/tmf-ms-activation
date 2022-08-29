package com.icthh.xm.tmf.ms.activation.service;

import com.google.common.base.Predicate;
import com.icthh.xm.commons.config.client.repository.TenantListRepository;
import com.icthh.xm.commons.exceptions.EntityNotFoundException;
import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.commons.lep.spring.LepService;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransactionState;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.icthh.xm.tmf.ms.activation.events.EventsSender;
import com.icthh.xm.tmf.ms.activation.repository.SagaEventRepository;
import com.icthh.xm.tmf.ms.activation.repository.SagaTransactionRepository;
import com.icthh.xm.tmf.ms.activation.resolver.TaskTypeKeyResolver;
import com.icthh.xm.tmf.ms.activation.resolver.TransactionTypeKeyResolver;
import com.icthh.xm.tmf.ms.activation.utils.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.google.common.base.Predicates.alwaysTrue;
import static com.google.common.base.Predicates.not;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.FAILED;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.ON_RETRY;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.WAIT_DEPENDS_TASK;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.WAIT_CONDITION_TASK;

@Slf4j
@Service
@LepService(group = "service.retry")
@RequiredArgsConstructor
@DependsOn("multiTenantLiquibase")
public class RetryService {

    private final ThreadPoolTaskScheduler threadPoolTaskScheduler;
    private final EventsSender eventsSender;
    private final SagaEventRepository sagaEventRepository;
    private final SagaTransactionRepository transactionRepository;
    private final TenantUtils tenantUtils;
    private final SeparateTransactionExecutor separateTransactionExecutor;

    private final TenantListRepository tenantListRepository;

    private final Map<String, Boolean> scheduledEventsId = new ConcurrentHashMap<>();

    private RetryService self;

    @PostConstruct
    public void postConstruct() {
        tenantListRepository.getTenants().forEach(tenant -> {
            tenantUtils.doInTenantContext(() -> {
                self.rescheduleAllEvents();
            }, tenant);
        });
    }

    @Transactional
    public void rescheduleAllEvents() {
        sagaEventRepository.findByStatus(ON_RETRY).forEach(this::doResend);
        sagaEventRepository.findByStatus(WAIT_DEPENDS_TASK).forEach(this::doResend);
        sagaEventRepository.findByStatus(WAIT_CONDITION_TASK).forEach(this::doResend);
    }

    @LogicExtensionPoint(value = "OnRetry", resolver = TaskTypeKeyResolver.class)
    public void retry(SagaEvent sagaEvent, SagaTransaction sagaTransaction, SagaTaskSpec task, SagaEvent.SagaEventStatus eventStatus) {
        long backOff = Math.min(task.getMaxBackOff(), sagaEvent.getBackOff() + task.getBackOff());
        sagaEvent.setBackOff(backOff);
        sagaEvent.setRetryNumber(sagaEvent.getRetryNumber() + 1);

        if (sagaEvent.getRetryNumber() > task.getRetryCount() && task.getRetryCount() >= 0) {
            log.warn("Retry limit exceeded for event {}. {} > {}", sagaEvent, sagaEvent.getRetryNumber(),
                task.getRetryCount());
            try {
                self.retryLimitExceededWithTaskTypeResolver(sagaEvent, sagaTransaction, task, eventStatus);
            } catch (Throwable e) { // Because of fact that groovy code can have compilation errors
                log.error("Error unable to start compensation lep: {}", e.getMessage(), e);
            }
            return;
        }

        scheduleRetry(sagaEvent, eventStatus);
    }

    public void retry(SagaEvent sagaEvent, SagaTransaction sagaTransaction, SagaTaskSpec sagaTaskSpec) {
        self.retry(sagaEvent, sagaTransaction, sagaTaskSpec, ON_RETRY);
    }

    public void retryForTaskWaitCondition(SagaEvent sagaEvent, SagaTransaction sagaTransaction, SagaTaskSpec sagaTaskSpec) {
        self.retry(sagaEvent, sagaTransaction, sagaTaskSpec, WAIT_CONDITION_TASK);
    }

    public void retryForWaitDependsTask(SagaEvent sagaEvent, SagaTransaction sagaTransaction, SagaTaskSpec sagaTaskSpec) {
        self.retry(sagaEvent, sagaTransaction, sagaTaskSpec, WAIT_DEPENDS_TASK);
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

    @Transactional
    public void changeTransactionState(String txId, SagaTransactionState state) {
        log.info("State of transaction:{} will be changed to: {}", txId, state);
        transactionRepository.findById(txId)
            .map(it -> it.setSagaTransactionState(state))
            .ifPresentOrElse(transactionRepository::save,
                () -> {
                    throw new EntityNotFoundException("Transaction with id " + txId + " not found");
                });
    }

    public void doResend(SagaEvent sagaEvent) {
        doResend(sagaEvent, self::removeAndSend);
    }

    private void doResend(SagaEvent sagaEvent, Consumer<SagaEvent> operation) {
        scheduledEventsId.remove(sagaEvent.getId());
        tenantUtils.doInTenantContext(() -> {
            try {
                log.info("Retry event {}. Send into broker.", sagaEvent);
                operation.accept(sagaEvent);
            } catch (Exception e) {
                log.info("Error has happend.", e);
                scheduleRetry(sagaEvent, ON_RETRY);
                throw e;
            }
        }, sagaEvent.getTenantKey());
    }

    @Transactional
    public void removeAndSend(SagaEvent sagaEvent) {
        this.removeAndSend(sagaEvent, not(SagaEvent::isInQueue));
    }

    @Transactional
    public void removeAndSend(SagaEvent sagaEvent, Predicate<SagaEvent> eventFilter) {
        sagaEventRepository.findById(sagaEvent.getId())
            .filter(eventFilter)
            .ifPresentOrElse(this::resendEvent,
                () -> log.warn("Event not allowed for resend {}", sagaEvent)
            );
    }

    private void resendEvent(SagaEvent event) {
        final String txId = event.getTransactionId();
        if (isTxCanceled(txId)) {
            log.info("Resend event not allowed. Transaction:{} canceled", txId);
            return;
        }
        log.info("Resend event: {}", event);
        event.markAsInQueue();
        event = sagaEventRepository.save(event);

        //we need to commit saga transaction state to DB before send event to kafka
        separateTransactionExecutor.doInSeparateTransaction(() -> {
            changeTransactionState(txId, SagaTransactionState.NEW);
            return Optional.empty();
        });
        eventsSender.sendEvent(event);
    }

    private boolean isTxCanceled(final String txId) {
        SagaTransaction sagaTransaction = transactionRepository.findById(txId).orElseThrow();
        return sagaTransaction.getSagaTransactionState().equals(SagaTransactionState.CANCELED);
    }

    @Autowired
    public void setSelf(RetryService self) {
        this.self = self;
    }


    @Transactional
    @LogicExtensionPoint("RetryLimitExceeded")
    public Map<String, Object> retryLimitExceeded(SagaEvent sagaEvent, SagaTaskSpec task, SagaEvent.SagaEventStatus eventStatus) {
        log.info("Retry limit exceeded for transaction {}, state will changed to {}", sagaEvent.getId(), FAILED);
        String eventId = sagaEvent.getId();
        changeTransactionState(sagaEvent.getTransactionId(), SagaTransactionState.FAILED);

        log.info("State of event:{} will be changed to: {}", sagaEvent.getId(), FAILED);
        sagaEventRepository.findById(eventId)
            .map(it -> it.setStatus(FAILED))
            .ifPresentOrElse(sagaEventRepository::save,
                () -> {
                    throw new EntityNotFoundException("Event with id " + eventId + " not found");
                });
        return new HashMap<>();
    }


    @Transactional
    @LogicExtensionPoint(value = "RetryLimitExceeded", resolver = TaskTypeKeyResolver.class)
    public Map<String, Object> retryLimitExceededWithTaskTypeResolver(SagaEvent sagaEvent, SagaTransaction sagaTransaction, SagaTaskSpec task, SagaEvent.SagaEventStatus eventStatus) {
        log.info("No handler for RetryLimitExceededWithTaskTypeResolver");
        return self.retryLimitExceededWithTransactionTypeResolver(sagaEvent, sagaTransaction, task, eventStatus);
    }

    @Transactional
    @LogicExtensionPoint(value = "RetryLimitExceeded", resolver = TransactionTypeKeyResolver.class)
    public Map<String, Object> retryLimitExceededWithTransactionTypeResolver(SagaEvent sagaEvent, SagaTransaction sagaTransaction, SagaTaskSpec task, SagaEvent.SagaEventStatus eventStatus) {
        log.info("No handler for RetryLimitExceededWithTaskTypeResolver");
        return self.retryLimitExceeded(sagaEvent, task, eventStatus);
    }

    // Method that allow to resend lost kafka event from db
    public void doResendInQueueEvent(SagaEvent sagaEvent) {
        doResend(sagaEvent, (event) -> self.removeAndSend(event, alwaysTrue()));
    }

}
