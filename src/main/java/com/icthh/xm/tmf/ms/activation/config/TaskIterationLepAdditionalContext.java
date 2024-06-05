package com.icthh.xm.tmf.ms.activation.config;

import com.icthh.xm.commons.lep.TargetProceedingLep;
import com.icthh.xm.commons.lep.api.BaseLepContext;
import com.icthh.xm.commons.lep.api.LepAdditionalContext;
import com.icthh.xm.commons.lep.api.LepAdditionalContextField;
import com.icthh.xm.commons.lep.api.LepBaseKey;
import com.icthh.xm.commons.lep.api.LepEngine;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static com.icthh.xm.tmf.ms.activation.config.TaskIterationLepAdditionalContext.TaskIterationField.FIELD_NAME;


@Component
@RequiredArgsConstructor
public class TaskIterationLepAdditionalContext implements LepAdditionalContext<Integer> {

    @Override
    public String additionalContextKey() {
        return FIELD_NAME;
    }

    @Override
    public Integer additionalContextValue() {
        return null;
    }

    @Override
    public Optional<Integer> additionalContextValue(BaseLepContext lepContext, LepEngine lepEngine, TargetProceedingLep lepMethod) {
        LepBaseKey lepBaseKey = lepMethod.getLepBaseKey();
        if ("tasks".equals(lepBaseKey.getGroup()) && "Task".equals(lepBaseKey.getBaseKey())) {
            SagaEvent sagaEvent = lepMethod.getParameter("sagaEvent", SagaEvent.class);
            return Optional.ofNullable(sagaEvent.getIteration());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Class<? extends LepAdditionalContextField> fieldAccessorInterface() {
        return TaskIterationField.class;
    }

    public interface TaskIterationField extends LepAdditionalContextField {
        String FIELD_NAME = "iteration";
        default Integer getIteration() {
            return (Integer)get(FIELD_NAME);
        }
    }

}
