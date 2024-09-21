package com.icthh.xm.tmf.ms.activation.utils;

import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyMap;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
public class JsonPathUtil {

    public static Object getByPath(Map<String, Object> taskContext, String path, SagaTaskSpec task) {
        if (isBlank(path)) {
            return null;
        }
        Map<String, Object> map = firstNonNull(taskContext, emptyMap());
        try {
            return JsonPath.read(map, path);
        } catch (JsonPathException e) {
            log.error("Error read from taskContext {} by json path {}", taskContext, path, e);
            if (TRUE.equals(task.getSkipIterableJsonPathError())) {
                return null;
            }
            throw e;
        }
    }

}
