package com.icthh.xm.tmf.ms.activation.domain.spec;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Objects;
import java.util.function.Predicate;

@Data
@JsonInclude(NON_NULL)
public class SagaTransactionSpec {

    private String key;
    private RetryPolicy retryPolicy;
    private Long retryCount;
    private Integer backOff;
    private Integer maxBackOff;
    private SagaTaskSpec tasks;
    private String onFinish;
    private String onFail;

    public static Predicate<SagaTransactionSpec> isEqualsKey(String key) {
        return it -> it != null && Objects.equals(key, it.getKey());
    }

}
