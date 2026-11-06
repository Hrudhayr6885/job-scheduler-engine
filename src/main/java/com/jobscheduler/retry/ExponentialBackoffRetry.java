package com.jobscheduler.retry;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Waits longer after each failure: base, 2x base, 4x base, ... up to maxDelay.
 *
 * Adds up to 10% random "jitter" so that, for example, 1,000 failed emails don't all retry
 * at the same millisecond and overload the mail server again (the "thundering herd" problem).
 */
public class ExponentialBackoffRetry implements RetryPolicy {

    private final long baseDelayMs;
    private final long maxDelayMs;

    public ExponentialBackoffRetry(long baseDelayMs, long maxDelayMs) {
        if (baseDelayMs <= 0 || maxDelayMs < baseDelayMs) {
            throw new IllegalArgumentException("Need 0 < baseDelayMs <= maxDelayMs");
        }
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    @Override
    public long nextDelayMs(int attemptNumber) {
        int exponent = Math.max(0, Math.min(attemptNumber - 1, 20)); // cap the exponent to avoid overflow
        long delay = Math.min(maxDelayMs, baseDelayMs * (1L << exponent));
        long jitter = ThreadLocalRandom.current().nextLong(delay / 10 + 1);
        return delay + jitter;
    }

    @Override
    public String describe() {
        return "exponential " + baseDelayMs + "ms..." + maxDelayMs + "ms";
    }
}
