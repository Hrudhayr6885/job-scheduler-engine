package com.jobscheduler.queue;

import com.jobscheduler.model.Job;

/**
 * Where ready jobs wait for a worker.
 *
 * [OOP: Abstraction] The dispatcher only needs enqueue/take. How ordering works is hidden.
 * [SOLID: DIP] The engine depends on this interface, so the in-memory heap could be
 *   replaced by a Redis/RabbitMQ-backed queue without touching the dispatcher.
 */
public interface JobQueue {

    /** @return false if the job can no longer be queued (e.g. it was cancelled). */
    boolean enqueue(Job job);

    /** Blocks until a job is available, then returns the most important one. */
    Job take() throws InterruptedException;

    boolean remove(Job job);

    int size();
}
