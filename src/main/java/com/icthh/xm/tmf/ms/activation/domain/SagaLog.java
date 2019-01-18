package com.icthh.xm.tmf.ms.activation.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.io.Serializable;

import static javax.persistence.EnumType.STRING;

@Data
@Entity
@Table(name = "saga_log")
@Accessors(chain = true)
public class SagaLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;
    @ManyToOne
    @JoinColumn(name = "saga_transaction_id")
    private SagaTransaction sagaTransaction;
    @Column(name = "event_type_key")
    private String eventTypeKey;
    @Enumerated(STRING)
    @Column(name = "log_type")
    private SagaLogType logType;

}
