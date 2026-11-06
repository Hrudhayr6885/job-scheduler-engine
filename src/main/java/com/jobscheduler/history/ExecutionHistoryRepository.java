package com.jobscheduler.history;

import com.jobscheduler.model.JobExecution;

import java.util.List;

/**
 * Stores every attempt of every job.
 *
 * [Design Pattern: Repository] Hides HOW history is stored (SQL, memory, files...) behind simple methods.
 * [SOLID: DIP] The engine depends on this interface. Tests use the in-memory version,
 *   the real app uses SQLite, and moving to PostgreSQL later means just one new class.
 * [SOLID: LSP] Any implementation can be swapped in without changing behaviour.
 */
public interface ExecutionHistoryRepository {

    void save(JobExecution execution);

    /** Newest first. */
    List<JobExecution> findRecent(int limit);

    /** Every attempt of one job, oldest first. */
    List<JobExecution> findByJobId(String jobId);

    int count();

    void clear();

    default void close() {
    }
}
