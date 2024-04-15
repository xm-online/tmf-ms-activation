package com.icthh.xm.tmf.ms.activation.domain.spec;

/** All strategy wait until all dependencies goes to files state (EVENT_END or REJECTED_BY_CONDITION) */
public enum DependsStrategy {

    /** When one dependency rejected, task will be rejected. When all dependency executed, task will be executed. */
    ALL_EXECUTED,

    /** When one or more dependency executed, task will be executed.
       When dependency all rejected, task will be rejected */
    AT_LEAST_ONE_EXECUTED,

    /** No matter dependency executed or reject, task will be executed. */
    ALL_EXECUTED_OR_REJECTED
}
