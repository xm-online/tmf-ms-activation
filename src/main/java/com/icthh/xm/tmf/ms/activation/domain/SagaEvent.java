package com.icthh.xm.tmf.ms.activation.domain;

import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.IN_QUEUE;
import static com.icthh.xm.tmf.ms.activation.domain.SagaEvent.SagaEventStatus.SUSPENDED;

import com.icthh.xm.tmf.ms.activation.repository.converter.MapToStringConverter;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Accessors(chain = true)
@ToString(exclude = "taskContext")
@Entity
@Table(name = "saga_event")
public class SagaEvent implements Serializable {

    @Id
    private String id = UUID.randomUUID().toString();
    @Column(name = "type_key")
    private String typeKey;
    @Column(name = "tenant_key")
    private String tenantKey;
    @Column(name = "parent_type_key")
    private String parentTypeKey;
    @Column(name = "transaction_id")
    private String transactionId;
    @Convert(converter = MapToStringConverter.class)
    @Column(name = "task_context")
    private Map<String, Object> taskContext = new HashMap<>();

    @Column(name = "back_off")
    private long backOff = 0;
    @Column(name = "retry_number")
    private long retryNumber = 0;
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private SagaEventStatus status;

    @Embedded
    private SagaEventError error;

    @Column(name = "create_date")
    private Instant createDate;

    public boolean isInQueue() {
        return IN_QUEUE == status;
    }

    public void markAsInQueue() {
        if (log.isTraceEnabled()) {
            log.trace("Mark event {} as IN_QUEUE", id);
        }
        this.status = IN_QUEUE;
    }

    public void setStatus(SagaEventStatus status) {
        if (log.isTraceEnabled()) {
            log.trace("Set event {} status to {}", id, status);
        }
        this.status = status;
    }

    public boolean isSuspended() {
        return SUSPENDED == status;
    }

    @RequiredArgsConstructor
    public enum SagaEventStatus {
        ON_RETRY(false),
        SUSPENDED(false),
        WAIT_DEPENDS_TASK(false),
        IN_QUEUE(false),
        WAIT_CONDITION_TASK(false),
        INVALID_SPECIFICATION(true),
        FAILED(true);

        final boolean finalState;
    }
}
