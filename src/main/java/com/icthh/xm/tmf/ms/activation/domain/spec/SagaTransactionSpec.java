package com.icthh.xm.tmf.ms.activation.domain.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.icthh.xm.commons.exceptions.BusinessException;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.icthh.xm.tmf.ms.activation.domain.spec.RetryPolicy.RETRY;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Data
@JsonInclude(NON_NULL)
@Accessors(chain = true)
public class SagaTransactionSpec {

    private String key;
    private RetryPolicy retryPolicy = RETRY;
    private Long retryCount = -1L;
    private Integer backOff = 5;
    private Integer maxBackOff = 30;
    private List<SagaTaskSpec> tasks;

    public static Predicate<SagaTransactionSpec> isEqualsKey(String key) {
        return it -> it != null && Objects.equals(key, it.getKey());
    }

    public List<SagaTaskSpec> getTasks() {
        if (tasks == null ){
            tasks = new ArrayList<>();
        }
        return tasks.stream().map(SagaTaskSpec::copy).collect(Collectors.toList());
    }

    public List<SagaTaskSpec> getFirstTasks() {
        Set<String> nextTasks = getTasks().stream().flatMap(task -> task.getNext().stream()).collect(toSet());
        return getTasks().stream().filter(task -> !nextTasks.contains(task.getKey())).collect(toList());
    }

    public SagaTaskSpec getTask(String typeKey) {
        return getTasks().stream()
            .filter(task -> task.getKey().equals(typeKey))
            .findAny()
            .orElseThrow(() ->
                new BusinessException("error.no.task.by.type.key.found", "No task by type key " + typeKey + " found")
            );
    }
}
