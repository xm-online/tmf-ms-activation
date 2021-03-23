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
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

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

    @Column(name = "create_date")
    private Instant createDate;

    public boolean isInQueue() {
        return IN_QUEUE == status;
    }

    public void markAsInQueue() {
        this.status = IN_QUEUE;
    }

    public boolean isSuspended() {
        return SUSPENDED == status;
    }

    public enum SagaEventStatus {
        ON_RETRY, SUSPENDED, WAIT_DEPENDS_TASK, IN_QUEUE, WAIT_CONDITION_TASK, INVALID_SPECIFICATION;
    }
}
