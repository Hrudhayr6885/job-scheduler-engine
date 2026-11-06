package com.jobscheduler.worker;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A fixed set of workers that are borrowed (acquire) and given back (release).
 *
 * [Design Pattern: Object Pool] Workers are created once and reused for thousands of jobs.
 * [SOLID: DIP] Which worker gets the job is delegated to an injected WorkerSelectionStrategy.
 */
public class WorkerPool {

    private final List<Worker> workers = new ArrayList<>();
    private final WorkerSelectionStrategy strategy;
    private final Clock clock;

    public WorkerPool(int size, WorkerSelectionStrategy strategy, Clock clock) {
        if (size <= 0) throw new IllegalArgumentException("Worker pool needs at least 1 worker");
        this.strategy = strategy;
        this.clock = clock;
        for (int i = 1; i <= size; i++) {
            workers.add(new Worker(i, clock.millis()));
        }
    }

    /** Blocks until a worker is idle, then marks it BUSY and returns it. */
    public synchronized Worker acquire() throws InterruptedException {
        while (true) {
            List<Worker> idle = new ArrayList<>();
            for (Worker worker : workers) {
                if (worker.isIdle()) idle.add(worker);
            }
            if (!idle.isEmpty()) {
                Worker chosen = strategy.select(idle);
                chosen.markBusy(clock.millis());
                return chosen;
            }
            wait(); // sleep until release() calls notifyAll()
        }
    }

    public synchronized void release(Worker worker) {
        worker.markIdle(clock.millis());
        notifyAll();
    }

    /** [OOP: Encapsulation] Read-only view: callers can look at workers but not add or remove them. */
    public List<Worker> getWorkers() {
        return Collections.unmodifiableList(workers);
    }

    public int size() {
        return workers.size();
    }

    public long busyCount() {
        return workers.stream().filter(w -> !w.isIdle()).count();
    }

    public double averageUtilization() {
        long now = clock.millis();
        return workers.stream().mapToDouble(w -> w.utilization(now)).average().orElse(0);
    }

    public String strategyName() {
        return strategy.name();
    }
}
