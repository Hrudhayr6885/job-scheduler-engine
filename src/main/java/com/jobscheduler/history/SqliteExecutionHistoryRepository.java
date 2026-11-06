package com.jobscheduler.history;

import com.jobscheduler.model.AttemptStatus;
import com.jobscheduler.model.JobExecution;
import com.jobscheduler.model.JobType;
import com.jobscheduler.model.Priority;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Execution history stored in SQLite: one file, data/job_history.db.
 *
 * Why SQLite?
 *   - zero setup: no database server to install, it ships inside the jar
 *   - real SQL: open the file with any SQLite tool and run queries
 *   - plenty fast for an embedded engine (WAL mode, thousands of inserts per second)
 *
 * All methods are synchronized because they share one JDBC connection.
 */
public class SqliteExecutionHistoryRepository implements ExecutionHistoryRepository {

    private static final String COLUMNS =
            "job_id, job_name, job_type, priority, attempt, worker_id, status, started_at, finished_at, duration_ms, error";

    private final Connection connection;

    public SqliteExecutionHistoryRepository(String filePath) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + filePath);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");   // faster writes, readers don't block writers
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS job_executions (
                            id          INTEGER PRIMARY KEY AUTOINCREMENT,
                            job_id      TEXT    NOT NULL,
                            job_name    TEXT    NOT NULL,
                            job_type    TEXT    NOT NULL,
                            priority    TEXT    NOT NULL,
                            attempt     INTEGER NOT NULL,
                            worker_id   TEXT,
                            status      TEXT    NOT NULL,
                            started_at  INTEGER NOT NULL,
                            finished_at INTEGER NOT NULL,
                            duration_ms INTEGER NOT NULL,
                            error       TEXT
                        )""");
                st.execute("CREATE INDEX IF NOT EXISTS idx_executions_job_id ON job_executions(job_id)");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open SQLite database at " + filePath, e);
        }
    }

    @Override
    public synchronized void save(JobExecution e) {
        String sql = "INSERT INTO job_executions (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, e.jobId());
            ps.setString(2, e.jobName());
            ps.setString(3, e.jobType().name());
            ps.setString(4, e.priority().name());
            ps.setInt(5, e.attempt());
            ps.setString(6, e.workerId());
            ps.setString(7, e.status().name());
            ps.setLong(8, e.startedAt());
            ps.setLong(9, e.finishedAt());
            ps.setLong(10, e.durationMs());
            ps.setString(11, e.error());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save execution of " + e.jobId(), ex);
        }
    }

    @Override
    public synchronized List<JobExecution> findRecent(int limit) {
        return query("SELECT " + COLUMNS + " FROM job_executions ORDER BY id DESC LIMIT ?", ps -> ps.setInt(1, limit));
    }

    @Override
    public synchronized List<JobExecution> findByJobId(String jobId) {
        return query("SELECT " + COLUMNS + " FROM job_executions WHERE job_id = ? ORDER BY attempt", ps -> ps.setString(1, jobId));
    }

    @Override
    public synchronized int count() {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM job_executions")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void clear() {
        try (Statement st = connection.createStatement()) {
            st.execute("DELETE FROM job_executions");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // closing on shutdown: nothing useful to do
        }
    }

    // ------------------------------------------------------------------ helpers

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private List<JobExecution> query(String sql, Binder binder) {
        List<JobExecution> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new JobExecution(
                            rs.getString("job_id"),
                            rs.getString("job_name"),
                            JobType.valueOf(rs.getString("job_type")),
                            Priority.valueOf(rs.getString("priority")),
                            rs.getInt("attempt"),
                            rs.getString("worker_id"),
                            AttemptStatus.valueOf(rs.getString("status")),
                            rs.getLong("started_at"),
                            rs.getLong("finished_at"),
                            rs.getString("error")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return rows;
    }
}
