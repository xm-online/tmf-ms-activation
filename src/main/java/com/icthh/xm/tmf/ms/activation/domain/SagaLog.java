package com.icthh.xm.tmf.ms.activation.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.io.Serializable;

import static javax.persistence.EnumType.STRING;

@Data
@Entity
@Accessors(chain = true)
public class SagaLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;
    @ManyToOne
    private SagaTransaction sagaTransaction;
    private String eventTypeKey;
    @Enumerated(STRING)
    private SagaLogType logType;

}
