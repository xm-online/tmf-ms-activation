package com.icthh.xm.tmf.ms.activation.domain;

import static jakarta.persistence.EnumType.STRING;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import com.icthh.xm.tmf.ms.activation.repository.converter.ActivationMapToStringConverter;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Entity
@Table(name = "saga_log")
@Accessors(chain = true)
public class SagaLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator", sequenceName = "hibernate_sequence")
    private Long id;
    @ManyToOne
    @JoinColumn(name = "saga_transaction_id")
    private SagaTransaction sagaTransaction;
    @Column(name = "event_type_key")
    private String eventTypeKey;
    @Enumerated(STRING)
    @Column(name = "log_type")
    private SagaLogType logType;

    @Column(name = "create_date")
    private Instant createDate;

    @Convert(converter = ActivationMapToStringConverter.class)
    @Column(name = "task_context")
    private Map<String, Object> taskContext = new HashMap<>();

    @Column(name = "iteration")
    private Integer iteration;

    @Column(name = "iterations_count")
    private Integer iterationsCount;
}
