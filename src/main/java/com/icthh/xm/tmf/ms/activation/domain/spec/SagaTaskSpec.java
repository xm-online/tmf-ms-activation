package com.icthh.xm.tmf.ms.activation.domain.spec;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy.RETRY;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
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
    private RetryPolicy retryPolicy = RETRY;
    private Long retryCount = -1l;
    private Integer backOff = 5;
    private Integer maxBackOff = 30;
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
        setIfNull(tx::getRetryPolicy, this::setRetryPolicy, this.getRetryPolicy());
        setIfNull(tx::getBackOff, this::setBackOff, this.getBackOff());
        setIfNull(tx::getMaxBackOff, this::setMaxBackOff, this.getMaxBackOff());
        setIfNull(tx::getRetryCount, this::setRetryCount, this.getRetryCount());
    }

    private static <T> void setIfNull(Supplier<T> getter, Consumer<T> setter, T value) {
        if (getter.get() == null) {
            setter.accept(value);
        }
    }
}
