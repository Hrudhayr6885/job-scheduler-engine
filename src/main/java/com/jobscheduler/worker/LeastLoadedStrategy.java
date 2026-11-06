package com.jobscheduler.worker;

import java.util.Comparator;
import java.util.List;

/** Gives the job to the idle worker that has done the least work so far, which spreads load evenly. */
public class LeastLoadedStrategy implements WorkerSelectionStrategy {

    @Override
    public Worker select(List<Worker> idleWorkers) {
        return idleWorkers.stream()
                .min(Comparator.comparingLong(Worker::getBusyTimeMs))
                .orElseThrow();
    }

    @Override
    public String name() {
        return "Least Loaded";
    }
}
