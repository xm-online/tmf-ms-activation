package com.icthh.xm.tmf.ms.activation.domain;

import static jakarta.persistence.EnumType.STRING;

import com.icthh.xm.tmf.ms.activation.repository.converter.ActivationMapToStringConverter;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "saga_transaction")
@ToString(exclude = "context")
@Accessors(chain = true)
public class SagaTransaction implements SagaType, Serializable {

    @Id
    @UuidGenerator
    private String id;
    @Column(name = "uniq_key")
    private String key;
    @NotNull
    @Column(name = "type_key")
    private String typeKey;
    @Convert(converter = ActivationMapToStringConverter.class)
    @Column(name = "context")
    private Map<String, Object> context = new HashMap<>();
    @Enumerated(STRING)
    @Column(name = "saga_transaction_state")
    private SagaTransactionState sagaTransactionState;

    @Column(name = "create_date")
    private Instant createDate;

    @Column(name = "specification_version")
    private String specificationVersion;

    @Column(name = "parent_event_id")
    public String parentEventId;
    @Column(name = "parent_tx_id")
    public String parentTxId;
}
