package com.icthh.xm.tmf.ms.activation.repository.converter;

import com.icthh.xm.commons.tenant.JsonMapperUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;

@Slf4j
@Converter
public class ActivationMapToStringConverter implements AttributeConverter<Map<String, Object>, String> {

    private final ObjectMapper mapper = JsonMapperUtils.getDefaultJsonMapper();

    public ActivationMapToStringConverter() {
    }

    @Override
    public String convertToDatabaseColumn(Map<String, Object> data) {
        try {
            return mapper.writeValueAsString(data != null ? data : new HashMap<>());
        } catch (JacksonException e) {
            log.warn("Error during JSON to String converting", e);
            return "";
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String data) {
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() {};
        try {
            return mapper.readValue(StringUtils.isNoneBlank(data) ? data : "{}", typeRef);
        } catch (JacksonException e) {
            log.warn("Error during String to JSON converting", e);
            return Collections.emptyMap();
        }
    }

}
