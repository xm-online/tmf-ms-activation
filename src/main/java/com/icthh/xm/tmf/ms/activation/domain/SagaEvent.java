package com.icthh.xm.tmf.ms.activation.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.UUID;

@Data
@Accessors(chain = true)
public class SagaEvent implements Serializable {

    private String id = UUID.randomUUID().toString();

    private String typeKey;

}
