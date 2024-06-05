package com.icthh.xm.tmf.ms.activation.config;

import com.icthh.xm.commons.lep.TargetProceedingLep;
import com.icthh.xm.commons.lep.api.BaseLepContext;
import com.icthh.xm.commons.lep.api.LepAdditionalContext;
import com.icthh.xm.commons.lep.api.LepAdditionalContextField;
import com.icthh.xm.commons.lep.api.LepBaseKey;
import com.icthh.xm.commons.lep.api.LepEngine;
import com.icthh.xm.tmf.ms.activation.domain.spec.SagaTaskSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static com.icthh.xm.tmf.ms.activation.config.TaskParametersLepAdditionalContext.TaskParametersField.FIELD_NAME;


@Component
@RequiredArgsConstructor
public class TaskParametersLepAdditionalContext implements LepAdditionalContext<Map<String, Object>> {

    @Override
    public String additionalContextKey() {
        return FIELD_NAME;
    }

    @Override
    public Map<String, Object> additionalContextValue() {
        return null;
    }

    @Override
    public Optional<Map<String, Object>> additionalContextValue(BaseLepContext lepContext, LepEngine lepEngine, TargetProceedingLep lepMethod) {
        LepBaseKey lepBaseKey = lepMethod.getLepBaseKey();
        if ("tasks".equals(lepBaseKey.getGroup()) && "Task".equals(lepBaseKey.getBaseKey())) {
            SagaTaskSpec sagaTaskSpec = lepMethod.getParameter("task", SagaTaskSpec.class);
            return Optional.ofNullable(sagaTaskSpec.getTaskParameters());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Class<? extends LepAdditionalContextField> fieldAccessorInterface() {
        return TaskParametersField.class;
    }

    public interface TaskParametersField extends LepAdditionalContextField {
        String FIELD_NAME = "taskParameters";
        default Map<String, Object> getTaskParameters() {
            return (Map<String, Object>)get(FIELD_NAME);
        }
    }

}
