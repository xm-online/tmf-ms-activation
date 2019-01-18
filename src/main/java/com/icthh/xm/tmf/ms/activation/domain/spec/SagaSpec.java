package com.icthh.xm.tmf.ms.activation.domain.spec;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec.isEqualsKey;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.icthh.xm.commons.exceptions.BusinessException;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@JsonInclude(NON_NULL)
@Accessors(chain = true)
public class SagaSpec {

    private List<SagaTransactionSpec> transactions;

    public SagaTransactionSpec getByType(String typeKey) {
        if (transactions == null) {
            throw notFound(typeKey);
        }
        return transactions.stream().filter(isEqualsKey(typeKey)).findFirst()
            .orElseThrow(() -> notFound(typeKey));
    }

    private BusinessException notFound(String type) {
        return new BusinessException("error.spec.not.found", "Spec for type " + type + " not found");
    }
}
