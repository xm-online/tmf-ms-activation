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
@Accessors(chain = true)
public class SagaTransaction implements Serializable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    private String id;
    @NotNull
    private String typeKey;
    @Convert(converter = MapToStringConverter.class)
    private Map<String, Object> context = new HashMap<>();
    @Enumerated(STRING)
    private SagaTransactionState sagaTransactionState;

}
