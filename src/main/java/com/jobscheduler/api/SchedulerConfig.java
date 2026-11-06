package com.jobscheduler.api;

/**
 * Engine settings.
 *
 * @param workerCount  how many jobs can run at the same time
 * @param agingStepMs  every agingStepMs of waiting raises a job's effective priority by one level
 */
public record SchedulerConfig(int workerCount, long agingStepMs) {

    public SchedulerConfig {
        if (workerCount <= 0) throw new IllegalArgumentException("workerCount must be > 0");
        if (agingStepMs <= 0) throw new IllegalArgumentException("agingStepMs must be > 0");
    }
}
