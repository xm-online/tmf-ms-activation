package com.icthh.xm.tmf.ms.activation.domain;

import static javax.persistence.EnumType.STRING;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import lombok.Data;
import lombok.experimental.Accessors;

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
