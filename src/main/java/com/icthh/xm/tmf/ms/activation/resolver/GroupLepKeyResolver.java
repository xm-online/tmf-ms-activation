package com.icthh.xm.tmf.ms.activation.resolver;

import static com.icthh.xm.commons.lep.XmLepConstants.EXTENSION_KEY_GROUP_MODE;
import static com.icthh.xm.commons.lep.XmLepConstants.EXTENSION_KEY_SEPARATOR;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.icthh.xm.commons.lep.SeparatorSegmentedLepKeyResolver;
import com.icthh.xm.lep.api.LepKey;
import com.icthh.xm.lep.api.LepManagerService;
import com.icthh.xm.lep.api.LepMethod;
import com.icthh.xm.lep.api.commons.GroupMode;
import com.icthh.xm.lep.api.commons.SeparatorSegmentedLepKey;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTransactionSpec;
import com.icthh.xm.tmf.ms.activation.service.SagaSpecService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
public abstract class GroupLepKeyResolver extends SeparatorSegmentedLepKeyResolver {

    private final SagaSpecService sagaSpecService;

    protected abstract String[] getAppendSegments(LepMethod method, SagaTransaction sagaTransaction);

    @Override
    protected LepKey resolveKey(SeparatorSegmentedLepKey inBaseKey, LepMethod method, LepManagerService managerService) {
        SagaTransaction sagaTransaction = getRequiredParam(method, "sagaTransaction", SagaTransaction.class);

        String[] lepKey = getAppendSegments(method, sagaTransaction);

        String separator = inBaseKey.getSeparator();
        String taskGroup = getTaskGroup(sagaTransaction, separator);
        String group = inBaseKey.getGroupKey().getId() + taskGroup + inBaseKey.getSegments()[inBaseKey.getGroupSegmentsSize()];

        SeparatorSegmentedLepKey baseKey = new SeparatorSegmentedLepKey(group, EXTENSION_KEY_SEPARATOR, EXTENSION_KEY_GROUP_MODE);
        GroupMode groupMode = new GroupMode.Builder().prefixAndIdIncludeGroup(baseKey.getGroupSegmentsSize()).build();
        return baseKey.append(lepKey, groupMode);
    }

    private String getTaskGroup(SagaTransaction sagaTransaction, String separator) {
        SagaTransactionSpec spec = sagaSpecService.getTransactionSpec(sagaTransaction);
        String group = spec.getGroup();
        group = StringUtils.strip(group, "/");
        if (isBlank(group)) {
            return separator;
        }
        group = group.replaceAll("/", separator);
        return separator + group + separator;
    }
}
