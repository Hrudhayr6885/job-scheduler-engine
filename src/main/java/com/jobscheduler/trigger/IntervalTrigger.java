package com.jobscheduler.trigger;

import java.time.Duration;
import java.util.Optional;

/** Runs a job repeatedly, e.g. "every 10 minutes". maxRuns <= 0 means forever. */
public class IntervalTrigger implements Trigger {

    private final long intervalMs;
    private final int maxRuns;

    public IntervalTrigger(Duration interval, int maxRuns) {
        if (interval.isNegative() || interval.isZero()) throw new IllegalArgumentException("interval must be > 0");
        this.intervalMs = interval.toMillis();
        this.maxRuns = maxRuns;
    }

    @Override
    public Optional<Long> nextFireTime(long now, int runsSoFar) {
        boolean finished = maxRuns > 0 && runsSoFar >= maxRuns;
        return finished ? Optional.empty() : Optional.of(now + intervalMs);
    }

    @Override
    public String describe() {
        return "every " + intervalMs / 1000.0 + "s" + (maxRuns > 0 ? ", " + maxRuns + " runs" : ", forever");
    }
}
