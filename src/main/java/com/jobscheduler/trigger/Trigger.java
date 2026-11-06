package com.jobscheduler.trigger;

import java.util.Optional;

/**
 * Decides WHEN a scheduled job should run.
 *
 * [Design Pattern: Strategy] DelayedTrigger, IntervalTrigger (and later CronTrigger)
 *   all answer the same question in different ways.
 * [SOLID: LSP] A finished trigger returns Optional.empty() instead of throwing, so the
 *   scheduler treats every trigger the same way.
 */
public interface Trigger {

    /**
     * @param now        current time (ms)
     * @param runsSoFar  how many times this schedule has already fired
     * @return when to fire next, or empty if the schedule is finished
     */
    Optional<Long> nextFireTime(long now, int runsSoFar);

    String describe();
}
