package com.jobscheduler.worker;

import java.util.List;

/** Hands jobs out in turn: W01, W02, W03, ... then back to W01 (skipping busy workers). */
public class RoundRobinStrategy implements WorkerSelectionStrategy {

    private int lastIndex = -1; // only called while WorkerPool holds its lock, so no extra sync needed

    @Override
    public Worker select(List<Worker> idleWorkers) {
        for (Worker worker : idleWorkers) {
            if (worker.getIndex() > lastIndex) {
                lastIndex = worker.getIndex();
                return worker;
            }
        }
        Worker first = idleWorkers.get(0); // wrapped around
        lastIndex = first.getIndex();
        return first;
    }

    @Override
    public String name() {
        return "Round Robin";
    }
}
