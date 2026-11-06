package com.jobscheduler.trigger;

import java.time.Duration;
import java.util.Optional;

/** Runs a job ONCE after a delay, e.g. "send the payment reminder in 30 minutes". */
public class DelayedTrigger implements Trigger {

    private final long delayMs;

    public DelayedTrigger(Duration delay) {
        this.delayMs = delay.toMillis();
    }

    @Override
    public Optional<Long> nextFireTime(long now, int runsSoFar) {
        return runsSoFar == 0 ? Optional.of(now + delayMs) : Optional.empty();
    }

    @Override
    public String describe() {
        return "once, after " + delayMs / 1000.0 + "s";
    }
}
