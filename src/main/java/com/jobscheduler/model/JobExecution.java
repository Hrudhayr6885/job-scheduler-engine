package com.jobscheduler.model;

/**
 * One attempt of one job. Each attempt becomes one row in the execution history (SQLite).
 *
 * [OOP: Encapsulation] A Java record: every field is final and there are no setters,
 *   so history can never be edited after it is written.
 */
public record JobExecution(
        String jobId,
        String jobName,
        JobType jobType,
        Priority priority,
        int attempt,
        String workerId,
        AttemptStatus status,
        long startedAt,
        long finishedAt,
        String error) {

    public long durationMs() {
        return finishedAt - startedAt;
    }
}
