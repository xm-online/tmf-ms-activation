package com.icthh.xm.tmf.ms.activation.domain.spec;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Data
@JsonInclude(NON_NULL)
@Accessors(chain = true)
public class SagaTaskSpec implements Serializable {

    private String key;
    private RetryPolicy retryPolicy;
    private Long retryCount;
    private Integer backOff;
    private Integer maxBackOff;
    private List<String> next;
    private List<String> depends;
    private Boolean isSuspendable;
    private Boolean saveTaskContext;
    private Map<String, Object> taskParameters;
    private DependsStrategy dependsStrategy;

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
        setIfNull(this::getSaveTaskContext, this::setSaveTaskContext, tx.getSaveTaskContext());
        setIfNull(this::getDependsStrategy, this::setDependsStrategy, tx.getDependsStrategy());
    }

    private static <T> void setIfNull(Supplier<T> getter, Consumer<T> setter, T value) {
        if (getter.get() == null) {
            setter.accept(value);
        }
    }

    public static SagaTaskSpec copy(SagaTaskSpec src){
        return SerializationUtils.clone(src);
    }
}
