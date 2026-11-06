package com.jobscheduler.events;

public enum EventType {
    JOB_SUBMITTED,
    JOB_STARTED,
    ATTEMPT_FINISHED,   // carries a JobExecution (one history row)
    RETRY_SCHEDULED,
    JOB_SUCCEEDED,
    JOB_DEAD,
    JOB_SKIPPED,
    JOB_CANCELLED
}
