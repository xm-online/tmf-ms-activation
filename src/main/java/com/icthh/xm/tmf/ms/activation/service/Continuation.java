package com.icthh.xm.tmf.ms.activation.service;

import lombok.Getter;

@Getter
public class Continuation {

    private boolean continuationFlag = false;

    public void continueTask() {
        continuationFlag = true;
    }

}
