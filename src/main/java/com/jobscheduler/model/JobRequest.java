package com.jobscheduler.model;

import com.jobscheduler.retry.ExponentialBackoffRetry;
import com.jobscheduler.retry.RetryPolicy;
import com.jobscheduler.trigger.Trigger;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Everything needed to submit a job.
 *
 * <pre>
 *   JobRequest request = JobRequest.builder("Invoice #42", JobType.INVOICE)
 *           .priority(Priority.MEDIUM)
 *           .payload("customer=C042;amount=1500")
 *           .maxRetries(3)
 *           .build();
 * </pre>
 *
 * [Design Pattern: Builder] Many optional fields → no huge constructor, and call sites read like English.
 * [OOP: Encapsulation] Immutable once built (all fields final, list copied), so it is safe to share between threads.
 */
public final class JobRequest {

    private final String id;              // optional custom id (handy for dependencies)
    private final String name;
    private final JobType type;
    private final Priority priority;
    private final String payload;         // "key=value;key=value"
    private final int maxRetries;
    private final long timeoutMs;
    private final RetryPolicy retryPolicy;
    private final List<String> dependsOn; // ids of jobs that must succeed first
    private final Trigger trigger;        // null = run now, otherwise delayed / recurring

    private JobRequest(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.type = b.type;
        this.priority = b.priority;
        this.payload = b.payload;
        this.maxRetries = b.maxRetries;
        this.timeoutMs = b.timeoutMs;
        this.retryPolicy = b.retryPolicy;
        this.dependsOn = List.copyOf(b.dependsOn);
        this.trigger = b.trigger;
    }

    public static Builder builder(String name, JobType type) {
        return new Builder(name, type);
    }

    /** Used by recurring schedules: same settings, new name, no trigger (runs immediately). */
    public JobRequest asOneOffCopy(String newName) {
        return new Builder(newName, type)
                .priority(priority)
                .payload(payload)
                .maxRetries(maxRetries)
                .timeoutMs(timeoutMs)
                .retryPolicy(retryPolicy)
                .dependsOn(dependsOn)
                .build();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public JobType getType() { return type; }
    public Priority getPriority() { return priority; }
    public String getPayload() { return payload; }
    public int getMaxRetries() { return maxRetries; }
    public long getTimeoutMs() { return timeoutMs; }
    public RetryPolicy getRetryPolicy() { return retryPolicy; }
    public List<String> getDependsOn() { return dependsOn; }
    public Trigger getTrigger() { return trigger; }

    public static final class Builder {
        private String id;
        private final String name;
        private final JobType type;
        private Priority priority = Priority.MEDIUM;
        private String payload = "";
        private int maxRetries = 3;
        private long timeoutMs = 5_000;
        private RetryPolicy retryPolicy = new ExponentialBackoffRetry(200, 5_000);
        private List<String> dependsOn = List.of();
        private Trigger trigger;

        private Builder(String name, JobType type) {
            this.name = Objects.requireNonNull(name, "name");
            this.type = Objects.requireNonNull(type, "type");
        }

        public Builder id(String id) { this.id = id; return this; }
        public Builder priority(Priority priority) { this.priority = Objects.requireNonNull(priority); return this; }
        public Builder payload(String payload) { this.payload = payload == null ? "" : payload; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder timeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; return this; }
        public Builder retryPolicy(RetryPolicy retryPolicy) { this.retryPolicy = Objects.requireNonNull(retryPolicy); return this; }
        public Builder dependsOn(List<String> jobIds) { this.dependsOn = jobIds; return this; }
        public Builder dependsOn(String... jobIds) { this.dependsOn = Arrays.asList(jobIds); return this; }
        public Builder trigger(Trigger trigger) { this.trigger = trigger; return this; }

        public JobRequest build() {
            // Fail fast: reject bad input at the door instead of failing later inside a worker.
            if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
            if (timeoutMs <= 0) throw new IllegalArgumentException("timeoutMs must be > 0");
            return new JobRequest(this);
        }
    }
}
