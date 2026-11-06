package com.jobscheduler.history;

import com.jobscheduler.model.JobExecution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** History kept in a list. Used by unit tests (fast, no files). Same contract as the SQLite version. */
public class InMemoryExecutionHistoryRepository implements ExecutionHistoryRepository {

    private final List<JobExecution> rows = new ArrayList<>();

    @Override
    public synchronized void save(JobExecution execution) {
        rows.add(execution);
    }

    @Override
    public synchronized List<JobExecution> findRecent(int limit) {
        List<JobExecution> copy = new ArrayList<>(rows);
        java.util.Collections.reverse(copy);
        return copy.subList(0, Math.min(limit, copy.size()));
    }

    @Override
    public synchronized List<JobExecution> findByJobId(String jobId) {
        return rows.stream()
                .filter(r -> r.jobId().equals(jobId))
                .sorted(Comparator.comparingInt(JobExecution::attempt))
                .toList();
    }

    @Override
    public synchronized int count() {
        return rows.size();
    }

    @Override
    public synchronized void clear() {
        rows.clear();
    }
}
