package com.jobscheduler.retry;

import com.jobscheduler.model.Job;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Jobs that failed permanently end up here so a person can look at them.
 *
 * [SOLID: SRP] Only stores dead jobs. Deciding WHEN a job is dead is JobExecutor's job.
 */
public class DeadLetterQueue {

    private final Queue<Job> deadJobs = new ConcurrentLinkedQueue<>();

    public void add(Job job) {
        deadJobs.add(job);
    }

    public List<Job> getAll() {
        return List.copyOf(deadJobs);
    }

    public int size() {
        return deadJobs.size();
    }
}
