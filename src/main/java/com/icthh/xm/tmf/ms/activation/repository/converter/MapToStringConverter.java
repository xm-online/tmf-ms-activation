package com.icthh.xm.tmf.ms.activation.repository.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Converter
public class MapToStringConverter implements AttributeConverter<Map<String, Object>, String> {

    private ObjectMapper mapper = new ObjectMapper();

    public MapToStringConverter() {
        mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public String convertToDatabaseColumn(Map<String, Object> data) {
        try {
            return mapper.writeValueAsString(data != null ? data : new HashMap<>());
        } catch (JsonProcessingException e) {
            log.warn("Error during JSON to String converting", e);
            return "";
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String data) {
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};
        try {
            return mapper.readValue(StringUtils.isNoneBlank(data) ? data : "{}", typeRef);
        } catch (IOException e) {
            log.warn("Error during String to JSON converting", e);
            return Collections.emptyMap();
        }
    }

}
