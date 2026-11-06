package com.jobscheduler.queue;

import com.jobscheduler.model.Job;

import java.time.Clock;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Priority queue with AGING, so LOW-priority jobs never starve.
 *
 * How aging works:
 *   effectivePriority(now) = weight + (now - enqueuedAt) / agingStepMs
 *   With agingStep = 5s, a LOW job (weight 1) that has waited 10s is as important
 *   as a brand-new HIGH job (weight 3).
 *
 * The trick: when two jobs are compared at the same moment, the "now" term cancels out:
 *   weightA - enqueuedAtA / step   vs   weightB - enqueuedAtB / step
 * So each job's sort key is FIXED when it is queued, and a normal heap still works.
 *
 * Data structure: PriorityBlockingQueue = thread-safe binary heap, O(log n) insert and take.
 * take() blocks while the queue is empty, so the dispatcher doesn't spin in a loop.
 */
public class PriorityJobQueue implements JobQueue {

    private final Clock clock;
    private final long agingStepMs;
    private final PriorityBlockingQueue<Job> heap;

    public PriorityJobQueue(Clock clock, long agingStepMs) {
        if (agingStepMs <= 0) throw new IllegalArgumentException("agingStepMs must be > 0");
        this.clock = clock;
        this.agingStepMs = agingStepMs;

        Comparator<Job> byAgedPriority = Comparator.comparingDouble(this::agingScore);
        Comparator<Job> order = byAgedPriority.reversed()             // highest score first
                .thenComparingLong(Job::getSequence);                 // then first come, first served
        this.heap = new PriorityBlockingQueue<>(1024, order);
    }

    double agingScore(Job job) {
        return job.getPriority().weight() - (double) job.getEnqueuedAt() / agingStepMs;
    }

    @Override
    public boolean enqueue(Job job) {
        if (!job.markQueued(clock.millis())) return false;
        heap.offer(job);
        return true;
    }

    @Override
    public Job take() throws InterruptedException {
        return heap.take();
    }

    @Override
    public boolean remove(Job job) {
        return heap.remove(job);
    }

    @Override
    public int size() {
        return heap.size();
    }
}
