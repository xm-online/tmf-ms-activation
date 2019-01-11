package com.icthh.xm.tmf.ms.activation.domain.spec;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec.isEqualsKey;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.icthh.xm.commons.exceptions.BusinessException;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(NON_NULL)
public class SagaSpec {

    private List<SagaTransactionSpec> sagaTransactionSpec;

    public SagaTransactionSpec getByType(String typeKey) {
        if (sagaTransactionSpec == null) {
            throw notFound(typeKey);
        }
        return sagaTransactionSpec.stream().filter(isEqualsKey(typeKey)).findFirst()
            .orElseThrow(() -> this.notFound(typeKey));
    }

    private BusinessException notFound(String type) {
        return new BusinessException("error.spec.not.found", "Spec for type " + type + " not found");
    }

}
