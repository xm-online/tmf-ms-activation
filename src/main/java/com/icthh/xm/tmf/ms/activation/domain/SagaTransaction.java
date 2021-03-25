package com.icthh.xm.tmf.ms.activation.domain;

import static javax.persistence.EnumType.STRING;

import com.icthh.xm.tmf.ms.activation.repository.converter.MapToStringConverter;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

@Data
@Entity
@Table(name = "saga_transaction")
@ToString(exclude = "context")
@Accessors(chain = true)
public class SagaTransaction implements Serializable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    private String id;
    @Column(name = "key")
    private String key;
    @NotNull
    @Column(name = "type_key")
    private String typeKey;
    @Convert(converter = MapToStringConverter.class)
    @Column(name = "context")
    private Map<String, Object> context = new HashMap<>();
    @Enumerated(STRING)
    @Column(name = "saga_transaction_state")
    private SagaTransactionState sagaTransactionState;

    @Column(name = "create_date")
    private Instant createDate;
}
