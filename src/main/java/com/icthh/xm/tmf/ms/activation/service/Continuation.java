package com.icthh.xm.tmf.ms.activation.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class Continuation {

    private boolean continuationFlag = false;

    public void continueTask() {
        log.info("Task call continue");
        continuationFlag = true;
    }

}
