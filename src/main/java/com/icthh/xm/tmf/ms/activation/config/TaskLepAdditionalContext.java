package com.icthh.xm.tmf.ms.activation.config;

import com.icthh.xm.commons.lep.TargetProceedingLep;
import com.icthh.xm.commons.lep.api.BaseLepContext;
import com.icthh.xm.commons.lep.api.LepAdditionalContext;
import com.icthh.xm.commons.lep.api.LepAdditionalContextField;
import com.icthh.xm.commons.lep.api.LepBaseKey;
import com.icthh.xm.commons.lep.api.LepEngine;
import com.icthh.xm.commons.lep.commons.CommonsExecutor;
import com.icthh.xm.tmf.ms.activation.config.TaskLepAdditionalContext.TaskContext;
import com.icthh.xm.tmf.ms.activation.domain.SagaEvent;
import com.icthh.xm.tmf.ms.activation.domain.SagaLog;
import com.icthh.xm.tmf.ms.activation.domain.SagaLogType;
import com.icthh.xm.tmf.ms.activation.domain.SagaTransaction;
import com.icthh.xm.tmf.ms.activation.repository.SagaLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.icthh.xm.tmf.ms.activation.config.TaskLepAdditionalContext.TaskContextField.FIELD_NAME;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_END;
import static com.icthh.xm.tmf.ms.activation.domain.SagaLogType.EVENT_START;
import static java.util.Collections.emptyMap;


@Component
@RequiredArgsConstructor
public class TaskLepAdditionalContext implements LepAdditionalContext<TaskContext> {

    private final SagaLogRepository sagaLogRepository;

    @Override
    public String additionalContextKey() {
        return FIELD_NAME;
    }

    @Override
    public TaskContext additionalContextValue() {
        return null;
    }

    @Override
    public Optional<TaskContext> additionalContextValue(BaseLepContext lepContext, LepEngine lepEngine, TargetProceedingLep lepMethod) {
        LepBaseKey lepBaseKey = lepMethod.getLepBaseKey();
        if ("tasks".equals(lepBaseKey.getGroup()) && "Task".equals(lepBaseKey.getBaseKey())) {
            SagaTransaction sagaTransaction = lepMethod.getParameter("sagaTransaction", SagaTransaction.class);
            SagaEvent sagaEvent = lepMethod.getParameter("sagaEvent", SagaEvent.class);
            TaskContext taskContext = new TaskContext(sagaLogRepository, sagaTransaction, sagaEvent);
            return Optional.of(taskContext);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Class<? extends LepAdditionalContextField> fieldAccessorInterface() {
        return TaskContextField.class;
    }

    public interface TaskContextField extends LepAdditionalContextField {
        String FIELD_NAME = "tasks";
        default TaskContext getTasks() {
            return (TaskContext)get(FIELD_NAME);
        }
    }

    // need to implement all methods from Map interface for js lep-s interop
    @RequiredArgsConstructor
    public static class TaskContext implements Map<String, Object> {

        private final SagaLogRepository sagaLogRepository;
        private final SagaTransaction sagaTransaction;
        private final SagaEvent sagaEvent;
        private final Map<String, Object> context = new HashMap<>();
        @Delegate(excludes = MapExcludes.class)
        private final Map<String, Object> delegate = emptyMap();

        @Override
        public Object get(Object inputTaskTypeKey) {
            String taskTypeKey = String.valueOf(inputTaskTypeKey);
            if ("input".equals(taskTypeKey) || "context".equals(taskTypeKey)) {
                return sagaEvent.getTaskContext();
            }
            if (sagaEvent.getTypeKey().equals(taskTypeKey)) {
                return Map.of("input", sagaEvent.getTaskContext(), "output", Map.of());
            }

            return context.computeIfAbsent(taskTypeKey, key -> {
                    List<SagaLog> logs = sagaLogRepository.getLogsBySagaTransactionIdAndTypeKey(sagaTransaction.getId(), taskTypeKey);
                    return Map.of(
                        "input", filterContext(logs, EVENT_START),
                        "output", filterContext(logs, EVENT_END)
                    );
                }
            );
        }

        @Override
        public boolean containsKey(Object taskTypeKey) {
            return true;
        }

        private static Map<String, Object> filterContext(List<SagaLog> logs, SagaLogType sagaLogType) {
            return logs.stream()
                .filter(it -> sagaLogType == it.getLogType())
                .findAny()
                .map(SagaLog::getTaskContext)
                .orElse(emptyMap());
        }

        public interface MapExcludes {
            Object get(Object key);
            boolean containsKey(Object key);
        }

    }
}
