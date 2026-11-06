package com.jobscheduler.model;

/**
 * Lifecycle of a job:
 *
 *   WAITING ──(dependencies done)──► QUEUED ──► RUNNING ──► SUCCEEDED
 *      │                               ▲           │
 *      │                               │           ├──► RETRY_SCHEDULED ──► (back to QUEUED)
 *      │                               │           └──► DEAD  (no retries left → dead letter queue)
 *      └──► SKIPPED  (a parent failed)                 any non-final state ──► CANCELLED
 *
 * [Design Pattern: State] Every state knows which states it may move to (canMoveTo).
 *   Job uses this to reject illegal moves such as SUCCEEDED → RUNNING.
 */
public enum JobStatus {
    WAITING,          // submitted, waiting for the jobs it depends on
    QUEUED,           // ready, sitting in the priority queue
    RUNNING,          // a worker is executing it
    RETRY_SCHEDULED,  // last attempt failed, will be queued again after a delay
    SUCCEEDED,        // final
    DEAD,             // final: failed permanently, moved to the dead letter queue
    SKIPPED,          // final: a job it depended on did not succeed
    CANCELLED;        // final: cancelled by the user

    public boolean isTerminal() {
        return this == SUCCEEDED || this == DEAD || this == SKIPPED || this == CANCELLED;
    }

    public boolean canMoveTo(JobStatus next) {
        return switch (this) {
            case WAITING -> next == QUEUED || next == SKIPPED || next == CANCELLED;
            case QUEUED -> next == RUNNING || next == CANCELLED;
            case RUNNING -> next == SUCCEEDED || next == RETRY_SCHEDULED || next == DEAD;
            case RETRY_SCHEDULED -> next == QUEUED || next == CANCELLED;
            default -> false; // final states never change
        };
    }
}
