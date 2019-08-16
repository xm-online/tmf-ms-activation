package com.icthh.xm.tmf.ms.activation.domain.spec;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy.RETRY;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Data
@JsonInclude(NON_NULL)
@Accessors(chain = true)
public class SagaTaskSpec implements Cloneable {

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

    @Override
    @SneakyThrows
    public SagaTaskSpec clone() {
        SagaTaskSpec cloned = null;
        cloned = (SagaTaskSpec) super.clone();
        cloned.next = next == null ? Collections.EMPTY_LIST : next.stream().collect(Collectors.toList());
        cloned.depends = depends == null ? Collections.EMPTY_LIST : depends.stream().collect(Collectors.toList());
        return cloned;
    }
}
