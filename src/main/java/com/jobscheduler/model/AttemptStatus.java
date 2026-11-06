package com.jobscheduler.model;

/** Result of ONE attempt of a job (a job may need several attempts). */
public enum AttemptStatus {
    SUCCESS,
    FAILED,
    TIMED_OUT
}
