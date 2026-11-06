package com.jobscheduler.events;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobExecution;

/**
 * Something that happened to a job.
 *
 * @param execution only set for ATTEMPT_FINISHED events, null otherwise
 */
public record JobEvent(EventType type, Job job, JobExecution execution, long timestamp) {

    public static JobEvent of(EventType type, Job job, long timestamp) {
        return new JobEvent(type, job, null, timestamp);
    }
}
