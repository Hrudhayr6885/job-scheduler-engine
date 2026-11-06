package com.jobscheduler.api;

import com.jobscheduler.dependency.DependencyManager;
import com.jobscheduler.events.EventBus;
import com.jobscheduler.events.EventType;
import com.jobscheduler.events.JobEvent;
import com.jobscheduler.execution.Dispatcher;
import com.jobscheduler.execution.JobExecutor;
import com.jobscheduler.handler.JobHandlerRegistry;
import com.jobscheduler.history.ExecutionHistoryRepository;
import com.jobscheduler.history.HistoryRecorder;
import com.jobscheduler.metrics.MetricsCollector;
import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobRequest;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.queue.JobQueue;
import com.jobscheduler.queue.PriorityJobQueue;
import com.jobscheduler.retry.DeadLetterQueue;
import com.jobscheduler.trigger.TriggerScheduler;
import com.jobscheduler.worker.WorkerPool;
import com.jobscheduler.worker.WorkerSelectionStrategy;

import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The ONE class users of the engine talk to.
 *
 *   scheduler.start();
 *   String id = scheduler.submit(request);
 *   scheduler.cancel(id);
 *
 * [Design Pattern: Facade] Callers never touch queues, workers, timers or the event bus directly.
 * [SOLID: DIP] Handlers, the history store, the worker strategy and the clock are passed in
 *   (constructor injection). Tests pass in-memory/fake versions, Main passes the real ones.
 * This class is also the "composition root": the one place where the internal parts are wired together.
 */
public class JobSchedulerService {

    private final Clock clock;
    private final JobHandlerRegistry registry;
    private final ExecutionHistoryRepository history;

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong();

    private final EventBus eventBus = new EventBus();
    private final DeadLetterQueue deadLetterQueue = new DeadLetterQueue();
    private final JobQueue queue;
    private final WorkerPool workerPool;
    private final DependencyManager dependencies;
    private final MetricsCollector metrics;
    private final TriggerScheduler triggers;
    private final JobExecutor executor;
    private final Dispatcher dispatcher;

    public JobSchedulerService(SchedulerConfig config,
                               JobHandlerRegistry registry,
                               ExecutionHistoryRepository history,
                               WorkerSelectionStrategy workerStrategy,
                               Clock clock) {
        this.clock = clock;
        this.registry = registry;
        this.history = history;

        this.queue = new PriorityJobQueue(clock, config.agingStepMs());
        this.workerPool = new WorkerPool(config.workerCount(), workerStrategy, clock);
        this.dependencies = new DependencyManager(jobs::get);
        this.metrics = new MetricsCollector(clock);
        this.triggers = new TriggerScheduler(this::submitNow, clock);
        this.executor = new JobExecutor(registry, workerPool, queue, deadLetterQueue, eventBus, clock);
        this.dispatcher = new Dispatcher(queue, workerPool, executor);

        dependencies.setCallbacks(queue::enqueue, job -> skip(job, "a job it depends on did not succeed"));

        // [Design Pattern: Observer] everything that reacts to job events subscribes here
        eventBus.subscribe(dependencies);
        eventBus.subscribe(metrics);
        eventBus.subscribe(new HistoryRecorder(history));
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        dispatcher.start();
    }

    public void shutdown() {
        triggers.shutdown();
        dispatcher.stop();
        executor.shutdown();
    }

    // ------------------------------------------------------------------ commands

    /**
     * Submit a job. If the request has a trigger it is scheduled for later instead.
     *
     * @return the job id (or "schedule:&lt;name&gt;" for scheduled requests)
     * @throws IllegalArgumentException for unknown job types, duplicate ids or dependency cycles
     */
    public String submit(JobRequest request) {
        if (request.getTrigger() != null) {
            triggers.schedule(request);
            return "schedule:" + request.getName();
        }
        return submitNow(request);
    }

    private String submitNow(JobRequest request) {
        if (!registry.supports(request.getType())) {
            throw new IllegalArgumentException("No handler registered for job type " + request.getType());
        }
        String id = request.getId() != null ? request.getId() : String.format("JOB-%06d", idCounter.incrementAndGet());
        Job job = new Job(id, request, clock.millis());
        if (jobs.putIfAbsent(id, job) != null) {
            throw new IllegalArgumentException("Duplicate job id: " + id);
        }

        DependencyManager.Decision decision;
        try {
            decision = dependencies.register(job);
        } catch (IllegalArgumentException cycle) {
            jobs.remove(id);
            throw cycle;
        }

        eventBus.publish(JobEvent.of(EventType.JOB_SUBMITTED, job, clock.millis()));
        switch (decision) {
            case READY -> queue.enqueue(job);
            case SKIP -> skip(job, "a job it depends on already failed");
            case WAITING -> { } // DependencyManager will queue it when its parents succeed
        }
        return id;
    }

    /** Cancels a job that hasn't started yet (queued, waiting or waiting to retry). */
    public boolean cancel(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null || !job.cancel(clock.millis())) return false;
        queue.remove(job);
        eventBus.publish(JobEvent.of(EventType.JOB_CANCELLED, job, clock.millis()));
        return true;
    }

    private void skip(Job job, String reason) {
        if (job.markSkipped(reason, clock.millis())) {
            eventBus.publish(JobEvent.of(EventType.JOB_SKIPPED, job, clock.millis()));
        }
    }

    // ------------------------------------------------------------------ queries (read-only)

    public Optional<Job> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public Collection<Job> getJobs() {
        return Collections.unmodifiableCollection(jobs.values());
    }

    public Map<JobStatus, Long> statusCounts() {
        Map<JobStatus, Long> counts = new EnumMap<>(JobStatus.class);
        for (JobStatus status : JobStatus.values()) counts.put(status, 0L);
        for (Job job : jobs.values()) counts.merge(job.getStatus(), 1L, Long::sum);
        return counts;
    }

    /** True when nothing is left to do: every job is final and no schedule will fire again. */
    public boolean isIdle() {
        return !triggers.hasActiveSchedules() && jobs.values().stream().allMatch(j -> j.getStatus().isTerminal());
    }

    public WorkerPool getWorkerPool() { return workerPool; }
    public MetricsCollector getMetrics() { return metrics; }
    public List<Job> getDeadJobs() { return deadLetterQueue.getAll(); }
    public List<TriggerScheduler.ScheduledJob> getScheduledJobs() { return triggers.getScheduledJobs(); }
    public ExecutionHistoryRepository getHistory() { return history; }
    public int queueSize() { return queue.size(); }
}
