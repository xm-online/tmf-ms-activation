package com.icthh.xm.tmf.ms.activation.utils;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

@Slf4j
@Component
public class TransactionUtils {

    @SneakyThrows
    @Transactional
    public <R> R withTransaction(TaskWithResult<R> task) {
        return task.doWork();
    }

    @SneakyThrows
    @Transactional
    public void withTransaction(Task task) {
        task.doWork();
    }

    @SneakyThrows
    @Transactional(propagation = REQUIRES_NEW)
    public <R> R withSeparateTransaction(TaskWithResult<R> task) {
        return task.doWork();
    }

    @SneakyThrows
    @Transactional(propagation = REQUIRES_NEW)
    public void withSeparateTransaction(Task task) {
        task.doWork();
    }

    public interface TaskWithResult<R> {
        R doWork() throws Exception;
    }

    public interface Task {
        void doWork() throws Exception;
    }
}
