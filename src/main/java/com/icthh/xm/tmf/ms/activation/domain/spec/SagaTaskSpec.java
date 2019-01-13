package com.icthh.xm.tmf.ms.activation.domain.spec;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(NON_NULL)
@Accessors(chain = true)
public class SagaTaskSpec {

    private String key;
    private RetryPolicy retryPolicy;
    private Long retryCount;
    private Integer backOff;
    private Integer maxBackOff;
    private List<String> next;
    private List<String> depends;

    public List<String> getNext() {
        if (next == null) {
            next = new ArrayList<>();
        }
        return next;
    }

    public List<String> getDepends() {
        if (depends == null) {
            depends = new ArrayList<>();
        }
        return depends;
    }

}
