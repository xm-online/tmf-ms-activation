package com.icthh.xm.tmf.ms.activation.domain;

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
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
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
    private int backOff = 0;
    @Column(name = "retry_number")
    private int retryNumber = 0;
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private SagaEventType status;

    @Column(name = "create_date")
    private Instant createDate;

    public enum SagaEventType {
        ON_RETRY, SUSPENDED
    }
}
