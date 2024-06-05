package com.icthh.xm.tmf.ms.activation.domain.spec;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec.isEqualsKey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.icthh.xm.commons.exceptions.BusinessException;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@JsonInclude(NON_NULL)
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SagaSpec {

    private List<SagaTransactionSpec> transactions;

    public List<SagaTransactionSpec> getTransactions() {
        if (transactions == null) {
            return List.of();
        }
        return transactions;
    }

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

    public static SagaSpec mergeSpec(SagaSpec spec1, SagaSpec spec2) {
        SagaSpec sagaSpec = new SagaSpec();
        List<SagaTransactionSpec> transactions = new ArrayList<>(spec1.getTransactions());
        transactions.addAll(spec2.getTransactions());
        sagaSpec.setTransactions(transactions);
        return sagaSpec;
    }
}
