package com.icthh.xm.tmf.ms.activation.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Data
@Embeddable
@Accessors(chain = true)
public class SagaEventError {

    @Column(name = "error_code")
    private String code;

    @Column(name = "error_description")
    private String description;
}
