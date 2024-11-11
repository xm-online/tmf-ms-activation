package com.icthh.xm.tmf.ms.activation.service;

import static java.lang.Boolean.TRUE;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@EqualsAndHashCode
@ToString
public class Continuation {

    private boolean continuationFlag;

    public Continuation(Boolean isSuspendable) {
        continuationFlag = !TRUE.equals(isSuspendable); // when isSuspendable is null, then continuationFlag have to be true
    }

    public void continueTask() {
        log.info("Task call continue");
        continuationFlag = true;
    }

    public void suspend() {
        log.info("Task call suspend");
        continuationFlag = false;
    }
}
