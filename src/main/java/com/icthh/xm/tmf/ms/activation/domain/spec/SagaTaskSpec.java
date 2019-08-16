package com.icthh.xm.tmf.ms.activation.domain.spec;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    private Boolean isSuspendable;

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

    public void applyAsDefaultTransactionConfig(SagaTransactionSpec tx) {
        setIfNull(this::getRetryPolicy, this::setRetryPolicy, tx.getRetryPolicy());
        setIfNull(this::getBackOff, this::setBackOff, tx.getBackOff());
        setIfNull(this::getMaxBackOff, this::setMaxBackOff, tx.getMaxBackOff());
        setIfNull(this::getRetryCount, this::setRetryCount, tx.getRetryCount());
    }

    private static <T> void setIfNull(Supplier<T> getter, Consumer<T> setter, T value) {
        if (getter.get() == null) {
            setter.accept(value);
        }
    }
}
