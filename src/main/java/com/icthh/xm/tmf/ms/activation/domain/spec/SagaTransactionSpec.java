package com.icthh.xm.tmf.ms.activation.domain.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.icthh.xm.tmf.ms.activation.service.SagaSpecService.InvalidSagaSpecificationException;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private String group;
    private String description;
    private RetryPolicy retryPolicy = RETRY;
    private Long retryCount = -1L;
    private Integer backOff = 5;
    private Integer maxBackOff = 30;
    /**
     * When this flag is true, transaction will change state to FINISHED after onFinish executed
     */
    private Boolean retryOnFinish;
    private Boolean saveTaskContext;
    // by default dependent task retries until all "depends" are completed
    // if this flag is true, then dependent task retries only after finish "depends"
    private Boolean checkDependsEventually;
    private DependsStrategy dependsStrategy = DependsStrategy.ALL_EXECUTED;
    private List<SagaTaskSpec> tasks;
    private String version;

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

    public Optional<SagaTaskSpec> findTask(String typeKey) {
        return getTasks().stream()
                .filter(task -> task.getKey().equals(typeKey))
                .findAny();
    }

    public SagaTaskSpec getTask(String typeKey) {
        return findTask(typeKey).orElseThrow(() ->
                new InvalidSagaSpecificationException("error.no.task.by.type.key.found",
                        "No task by type key " + typeKey + " found")
            );
    }

    public List<String> findDependentTasks(String key) {
        return getTasks().stream()
                .filter(task -> task.getDepends() != null && task.getDepends().contains(key))
                .map(SagaTaskSpec::getKey)
                .collect(toList());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SagaTaskSpec{");
        if (key != null) {
            sb.append("key='").append(key).append('\'');
        }
        if (group != null) {
            sb.append("group='").append(key).append('\'');
        }
        if (retryCount != null) {
            sb.append(", retryCount=").append(retryCount);
        }
        if (saveTaskContext != null) {
            sb.append(", saveTaskContext=").append(saveTaskContext);
        }
        if (dependsStrategy != null) {
            sb.append(", dependsStrategy=").append(dependsStrategy);
        }
        sb.append('}');
        return sb.toString();
    }
}
