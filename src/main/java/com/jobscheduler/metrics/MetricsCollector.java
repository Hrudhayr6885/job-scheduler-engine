package com.jobscheduler.metrics;

import com.jobscheduler.events.JobEvent;
import com.jobscheduler.events.JobEventListener;
import com.jobscheduler.model.AttemptStatus;
import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobExecution;
import com.jobscheduler.model.JobType;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects live numbers for the dashboards (throughput, wait time, retries...).
 *
 * [Design Pattern: Observer] Subscribes to the EventBus; the engine never calls it directly.
 * [SOLID: SRP] Only counts. Showing the numbers is the dashboards' job.
 */
public class MetricsCollector implements JobEventListener {

    private final Clock clock;
    private final long startedAt;

    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong succeeded = new AtomicLong();
    private final AtomicLong dead = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();
    private final AtomicLong cancelled = new AtomicLong();
    private final AtomicLong retries = new AtomicLong();
    private final AtomicLong failedAttempts = new AtomicLong();
    private final AtomicLong timeouts = new AtomicLong();
    private final LongAdder totalWaitMs = new LongAdder();
    private final LongAdder waitSamples = new LongAdder();
    private final Map<JobType, LongAdder> attemptsByType = new EnumMap<>(JobType.class);
    private final Map<JobType, LongAdder> execMsByType = new EnumMap<>(JobType.class);
    private volatile long lastFinishedAt;

    public MetricsCollector(Clock clock) {
        this.clock = clock;
        this.startedAt = clock.millis();
        for (JobType type : JobType.values()) {
            attemptsByType.put(type, new LongAdder());
            execMsByType.put(type, new LongAdder());
        }
    }

    @Override
    public void onEvent(JobEvent event) {
        Job job = event.job();
        switch (event.type()) {
            case JOB_SUBMITTED -> submitted.incrementAndGet();
            case JOB_STARTED -> {
                totalWaitMs.add(job.getStartedAt() - job.getEnqueuedAt()); // time spent in the queue
                waitSamples.increment();
            }
            case ATTEMPT_FINISHED -> {
                JobExecution e = event.execution();
                attemptsByType.get(e.jobType()).increment();
                execMsByType.get(e.jobType()).add(e.durationMs());
                if (e.status() == AttemptStatus.FAILED) failedAttempts.incrementAndGet();
                if (e.status() == AttemptStatus.TIMED_OUT) timeouts.incrementAndGet();
            }
            case RETRY_SCHEDULED -> retries.incrementAndGet();
            case JOB_SUCCEEDED -> {
                succeeded.incrementAndGet();
                lastFinishedAt = event.timestamp();
            }
            case JOB_DEAD -> {
                dead.incrementAndGet();
                lastFinishedAt = event.timestamp();
            }
            case JOB_SKIPPED -> skipped.incrementAndGet();
            case JOB_CANCELLED -> cancelled.incrementAndGet();
        }
    }

    public long getSubmitted() { return submitted.get(); }
    public long getSucceeded() { return succeeded.get(); }
    public long getDead() { return dead.get(); }
    public long getSkipped() { return skipped.get(); }
    public long getCancelled() { return cancelled.get(); }
    public long getRetries() { return retries.get(); }
    public long getFailedAttempts() { return failedAttempts.get(); }
    public long getTimeouts() { return timeouts.get(); }

    public long uptimeMs() {
        return clock.millis() - startedAt;
    }

    public double averageWaitMs() {
        long samples = waitSamples.sum();
        return samples == 0 ? 0 : (double) totalWaitMs.sum() / samples;
    }

    public long attempts(JobType type) {
        return attemptsByType.get(type).sum();
    }

    public double averageExecMs(JobType type) {
        long count = attemptsByType.get(type).sum();
        return count == 0 ? 0 : (double) execMsByType.get(type).sum() / count;
    }

    /** Successful jobs per minute, measured from start until the last job finished. */
    public double throughputPerMinute() {
        long end = lastFinishedAt == 0 ? clock.millis() : lastFinishedAt;
        long elapsed = Math.max(1, end - startedAt);
        return succeeded.get() * 60_000.0 / elapsed;
    }
}
