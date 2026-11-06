package com.jobscheduler.model;

import com.jobscheduler.retry.RetryPolicy;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A job inside the engine = the submitted JobRequest + its live state.
 *
 * [OOP: Encapsulation] The status is private and there is NO setStatus().
 *   It only changes through intention-revealing methods (markQueued, startAttempt,
 *   markSucceeded, ...) and each one refuses an illegal move.
 * [OOP: Composition] A Job HAS-A JobRequest (its settings) instead of extending it.
 * [Design Pattern: State] Which moves are legal is decided by JobStatus.canMoveTo().
 *
 * Thread-safety: workers, the dispatcher and the retry timer all touch jobs from
 * different threads, so every state change is synchronized.
 */
public class Job {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final String id;
    private final long sequence;      // tie-breaker: same priority → first come, first served
    private final JobRequest request;
    private final long createdAt;

    private JobStatus status = JobStatus.WAITING;
    private int attempts;
    private long enqueuedAt;
    private long startedAt;
    private long finishedAt;
    private String workerId;
    private String lastError;

    public Job(String id, JobRequest request, long createdAt) {
        this.id = id;
        this.sequence = SEQUENCE.incrementAndGet();
        this.request = request;
        this.createdAt = createdAt;
    }

    // ------------------------------------------------------------------ state changes

    /** @return false if the job can't be queued any more (e.g. it was cancelled). */
    public synchronized boolean markQueued(long now) {
        if (!status.canMoveTo(JobStatus.QUEUED)) return false;
        status = JobStatus.QUEUED;
        enqueuedAt = now;
        return true;
    }

    /** @return false if the job is no longer QUEUED (e.g. cancelled while waiting for a worker). */
    public synchronized boolean startAttempt(String workerId, long now) {
        if (status != JobStatus.QUEUED) return false;
        status = JobStatus.RUNNING;
        attempts++;
        this.workerId = workerId;
        this.startedAt = now;
        return true;
    }

    public synchronized void markSucceeded(long now) {
        moveTo(JobStatus.SUCCEEDED);
        finishedAt = now;
        lastError = null;
    }

    public synchronized void markRetryScheduled(String error) {
        moveTo(JobStatus.RETRY_SCHEDULED);
        lastError = error;
    }

    public synchronized void markDead(String error, long now) {
        moveTo(JobStatus.DEAD);
        lastError = error;
        finishedAt = now;
    }

    public synchronized boolean markSkipped(String reason, long now) {
        if (!status.canMoveTo(JobStatus.SKIPPED)) return false;
        status = JobStatus.SKIPPED;
        lastError = reason;
        finishedAt = now;
        return true;
    }

    public synchronized boolean cancel(long now) {
        if (!status.canMoveTo(JobStatus.CANCELLED)) return false;
        status = JobStatus.CANCELLED;
        finishedAt = now;
        return true;
    }

    /** attempts includes the first run, so maxRetries = 3 allows 4 attempts in total. */
    public synchronized boolean hasRetriesLeft() {
        return attempts <= request.getMaxRetries();
    }

    private void moveTo(JobStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Job " + id + ": illegal transition " + status + " -> " + next);
        }
        status = next;
    }

    // ------------------------------------------------------------------ read-only getters

    public String getId() { return id; }
    public long getSequence() { return sequence; }
    public String getName() { return request.getName(); }
    public JobType getType() { return request.getType(); }
    public Priority getPriority() { return request.getPriority(); }
    public String getPayload() { return request.getPayload(); }
    public long getTimeoutMs() { return request.getTimeoutMs(); }
    public int getMaxRetries() { return request.getMaxRetries(); }
    public RetryPolicy getRetryPolicy() { return request.getRetryPolicy(); }
    public List<String> getDependsOn() { return request.getDependsOn(); }
    public long getCreatedAt() { return createdAt; }

    public synchronized JobStatus getStatus() { return status; }
    public synchronized int getAttempts() { return attempts; }
    public synchronized long getEnqueuedAt() { return enqueuedAt; }
    public synchronized long getStartedAt() { return startedAt; }
    public synchronized long getFinishedAt() { return finishedAt; }
    public synchronized String getWorkerId() { return workerId; }
    public synchronized String getLastError() { return lastError; }

    @Override
    public String toString() {
        return id + " (" + getName() + ", " + getPriority() + ", " + getStatus() + ")";
    }
}
