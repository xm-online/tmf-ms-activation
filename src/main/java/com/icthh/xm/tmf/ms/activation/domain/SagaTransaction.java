package com.icthh.xm.tmf.ms.activation.domain;

import com.icthh.xm.tmf.ms.activation.repository.converter.MapToStringConverter;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static javax.persistence.EnumType.STRING;

@Data
@Entity
@Table(name = "saga_transaction")
@Accessors(chain = true)
public class SagaTransaction implements Serializable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    private String id;
    @NotNull
    @Column(name = "type_key")
    private String typeKey;
    @Convert(converter = MapToStringConverter.class)
    @Column(name = "context")
    private Map<String, Object> context = new HashMap<>();
    @Enumerated(STRING)
    @Column(name = "saga_transaction_state")
    private SagaTransactionState sagaTransactionState;

}
