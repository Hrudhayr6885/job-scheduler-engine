package com.jobscheduler.retry;

/** Always waits the same amount of time between retries. */
public class FixedDelayRetry implements RetryPolicy {

    private final long delayMs;

    public FixedDelayRetry(long delayMs) {
        if (delayMs < 0) throw new IllegalArgumentException("delayMs must be >= 0");
        this.delayMs = delayMs;
    }

    @Override
    public long nextDelayMs(int attemptNumber) {
        return delayMs;
    }

    @Override
    public String describe() {
        return "fixed " + delayMs + "ms";
    }
}
