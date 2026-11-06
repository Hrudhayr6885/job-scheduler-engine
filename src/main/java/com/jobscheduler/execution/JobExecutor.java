package com.jobscheduler.execution;

import com.jobscheduler.events.EventBus;
import com.jobscheduler.events.EventType;
import com.jobscheduler.events.JobEvent;
import com.jobscheduler.handler.JobHandlerRegistry;
import com.jobscheduler.handler.JobTimeoutException;
import com.jobscheduler.handler.NonRetryableException;
import com.jobscheduler.model.AttemptStatus;
import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobExecution;
import com.jobscheduler.queue.JobQueue;
import com.jobscheduler.retry.DeadLetterQueue;
import com.jobscheduler.util.NamedThreadFactory;
import com.jobscheduler.worker.Worker;
import com.jobscheduler.worker.WorkerPool;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runs ONE attempt of ONE job on ONE worker, then decides what happens next:
 *   success → SUCCEEDED
 *   failure → retry later (with backoff), or DEAD (dead letter queue) if no retries are left.
 *
 * [SOLID: SRP] Execution and failure handling only. Picking the job and the worker is the Dispatcher's job.
 * [SOLID: DIP] Depends on abstractions: JobQueue, RetryPolicy, JobHandler (through the registry).
 */
public class JobExecutor {

    private final JobHandlerRegistry registry;
    private final WorkerPool workerPool;
    private final JobQueue queue;
    private final DeadLetterQueue deadLetterQueue;
    private final EventBus events;
    private final Clock clock;
    private final ScheduledExecutorService retryTimer =
            Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("retry-timer"));

    public JobExecutor(JobHandlerRegistry registry, WorkerPool workerPool, JobQueue queue,
                       DeadLetterQueue deadLetterQueue, EventBus events, Clock clock) {
        this.registry = registry;
        this.workerPool = workerPool;
        this.queue = queue;
        this.deadLetterQueue = deadLetterQueue;
        this.events = events;
        this.clock = clock;
    }

    public void execute(Job job, Worker worker) {
        if (!job.startAttempt(worker.getId(), clock.millis())) { // cancelled while waiting for a worker
            workerPool.release(worker);
            return;
        }
        try {
            worker.assignJob(job.getId());
            events.publish(JobEvent.of(EventType.JOB_STARTED, job, clock.millis()));

            AttemptStatus outcome = AttemptStatus.SUCCESS;
            Exception error = null;
            try {
                // [OOP: Polymorphism] invoice, email or report: this line doesn't care which one
                registry.get(job.getType()).handle(job);
            } catch (JobTimeoutException e) {
                outcome = AttemptStatus.TIMED_OUT;
                error = e;
            } catch (Exception e) {
                outcome = AttemptStatus.FAILED;
                error = e;
            }

            long now = clock.millis();
            JobExecution attempt = new JobExecution(job.getId(), job.getName(), job.getType(), job.getPriority(),
                    job.getAttempts(), worker.getId(), outcome, job.getStartedAt(), now,
                    error == null ? null : describe(error));
            events.publish(new JobEvent(EventType.ATTEMPT_FINISHED, job, attempt, now));

            if (outcome == AttemptStatus.SUCCESS) {
                job.markSucceeded(now);
                events.publish(JobEvent.of(EventType.JOB_SUCCEEDED, job, now));
            } else {
                handleFailure(job, error, now);
            }
        } finally {
            workerPool.release(worker); // always give the worker back, even if something above blew up
        }
    }

    private void handleFailure(Job job, Exception error, long now) {
        String reason = describe(error);
        boolean retryable = !(error instanceof NonRetryableException); // bad input won't fix itself

        if (retryable && job.hasRetriesLeft()) {
            long delayMs = job.getRetryPolicy().nextDelayMs(job.getAttempts());
            job.markRetryScheduled(reason);
            events.publish(JobEvent.of(EventType.RETRY_SCHEDULED, job, now));
            // enqueue() returns false (and does nothing) if the job was cancelled in the meantime
            retryTimer.schedule(() -> queue.enqueue(job), delayMs, TimeUnit.MILLISECONDS);
        } else {
            job.markDead(reason, now);
            deadLetterQueue.add(job);
            events.publish(JobEvent.of(EventType.JOB_DEAD, job, now));
        }
    }

    private static String describe(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    public void shutdown() {
        retryTimer.shutdownNow();
    }
}
