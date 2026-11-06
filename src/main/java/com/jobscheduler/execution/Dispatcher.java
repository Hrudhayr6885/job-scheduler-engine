package com.jobscheduler.execution;

import com.jobscheduler.model.Job;
import com.jobscheduler.queue.JobQueue;
import com.jobscheduler.util.NamedThreadFactory;
import com.jobscheduler.worker.Worker;
import com.jobscheduler.worker.WorkerPool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The heart of the engine, one simple loop:
 *     take the most important job  →  get a free worker  →  run it on a worker thread
 *
 * [Design Pattern: Producer–Consumer] submit() produces jobs into the queue; the dispatcher consumes them.
 * [SOLID: SRP] Only matches jobs to workers. It doesn't run, retry or record anything itself.
 */
public class Dispatcher {

    private final JobQueue queue;
    private final WorkerPool workerPool;
    private final JobExecutor executor;
    private final ExecutorService workerThreads;
    private volatile boolean running;
    private Thread loopThread;

    public Dispatcher(JobQueue queue, WorkerPool workerPool, JobExecutor executor) {
        this.queue = queue;
        this.workerPool = workerPool;
        this.executor = executor;
        this.workerThreads = Executors.newFixedThreadPool(workerPool.size(), new NamedThreadFactory("worker-thread"));
    }

    public void start() {
        running = true;
        loopThread = new NamedThreadFactory("dispatcher").newThread(this::dispatchLoop);
        loopThread.start();
    }

    private void dispatchLoop() {
        while (running) {
            try {
                Job job = queue.take();                // waits until there is a job (highest priority first)
                Worker worker = workerPool.acquire();  // waits until a worker is free (chosen by the strategy)
                workerThreads.submit(() -> runSafely(job, worker));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void runSafely(Job job, Worker worker) {
        try {
            executor.execute(job, worker);
        } catch (RuntimeException e) {
            System.err.println("[Dispatcher] unexpected error while running " + job.getId() + ": " + e);
        }
    }

    public void stop() {
        running = false;
        if (loopThread != null) loopThread.interrupt();
        workerThreads.shutdownNow();
    }
}
