package com.jobscheduler.history;

import com.jobscheduler.events.EventType;
import com.jobscheduler.events.JobEvent;
import com.jobscheduler.events.JobEventListener;

/**
 * Writes every finished attempt to the history repository.
 *
 * [Design Pattern: Observer] Listens to ATTEMPT_FINISHED events; the executor never calls it directly.
 * [SOLID: SRP] The repository knows how to STORE; this class knows WHEN to store.
 */
public class HistoryRecorder implements JobEventListener {

    private final ExecutionHistoryRepository repository;

    public HistoryRecorder(ExecutionHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onEvent(JobEvent event) {
        if (event.type() == EventType.ATTEMPT_FINISHED) {
            repository.save(event.execution());
        }
    }
}
