package com.jobscheduler.worker;

import java.util.List;

/**
 * Picks which idle worker gets the next job.
 *
 * [Design Pattern: Strategy] RoundRobin / LeastLoaded can be swapped without touching WorkerPool.
 * [SOLID: OCP] A new strategy (e.g. "FastestWorkerFirst") is just a new class.
 * [OOP: Polymorphism] WorkerPool calls select(...) and gets different behaviour per strategy.
 */
public interface WorkerSelectionStrategy {

    /** @param idleWorkers never empty, in worker-index order. */
    Worker select(List<Worker> idleWorkers);

    String name();
}
