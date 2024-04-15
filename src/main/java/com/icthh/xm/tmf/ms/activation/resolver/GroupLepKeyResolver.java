package com.icthh.xm.tmf.ms.activation.resolver;

import com.icthh.xm.lep.api.LepKeyResolver;
import com.icthh.xm.lep.api.LepMethod;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import com.icthh.xm.tmf.ms.activation.service.SagaSpecService;
import lombok.RequiredArgsConstructor;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.strip;

@RequiredArgsConstructor
public abstract class GroupLepKeyResolver implements LepKeyResolver {

    private final SagaSpecService sagaSpecService;

    @Override
    public String group(LepMethod method) {
        SagaTransaction sagaTransaction = method.getParameter("sagaTransaction", SagaTransaction.class);
        SagaTransactionSpec spec = sagaSpecService.getTransactionSpec(sagaTransaction);
        String group = spec.getGroup();
        group = strip(group, "/");
        String baseGroup = LepKeyResolver.super.group(method);
        if (isBlank(group)) {
            return baseGroup;
        }
        return baseGroup + "." + group.replace("/", ".");
    }

}
