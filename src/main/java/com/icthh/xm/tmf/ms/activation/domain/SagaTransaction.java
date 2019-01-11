package com.icthh.xm.tmf.ms.activation.domain;

import com.icthh.xm.tmf.ms.activation.repository.converter.MapToStringConverter;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@Entity
@Accessors(chain = true)
public class SagaTransaction implements Serializable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    private String id;

    private String typeKey;

    @Convert(converter = MapToStringConverter.class)
    private Map<String, Object> context = new HashMap<>();

}
