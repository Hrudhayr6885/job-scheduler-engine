package com.jobscheduler.worker;

/**
 * A worker slot: runs one job at a time and remembers how busy it has been.
 *
 * [OOP: Encapsulation] The busy-time bookkeeping is private. markBusy/markIdle are
 *   package-private, so only WorkerPool (same package) can flip a worker between
 *   IDLE and BUSY. Nobody else can corrupt the utilization numbers.
 */
public class Worker {

    private final int index;
    private final String id;
    private final long createdAt;

    private WorkerStatus status = WorkerStatus.IDLE;
    private String currentJobId;
    private long busySince;
    private long busyTimeMs;
    private long tasksHandled;

    Worker(int index, long createdAt) {
        this.index = index;
        this.id = String.format("W%02d", index);
        this.createdAt = createdAt;
    }

    synchronized void markBusy(long now) {
        status = WorkerStatus.BUSY;
        busySince = now;
    }

    synchronized void markIdle(long now) {
        if (status == WorkerStatus.BUSY) {
            busyTimeMs += now - busySince;
            tasksHandled++;
        }
        status = WorkerStatus.IDLE;
        currentJobId = null;
    }

    public synchronized void assignJob(String jobId) {
        this.currentJobId = jobId;
    }

    /** % of its lifetime this worker spent doing work. */
    public synchronized double utilization(long now) {
        long lifetime = Math.max(1, now - createdAt);
        long busy = busyTimeMs + (status == WorkerStatus.BUSY ? now - busySince : 0);
        return 100.0 * busy / lifetime;
    }

    public int getIndex() { return index; }
    public String getId() { return id; }
    public synchronized boolean isIdle() { return status == WorkerStatus.IDLE; }
    public synchronized WorkerStatus getStatus() { return status; }
    public synchronized String getCurrentJobId() { return currentJobId; }
    public synchronized long getBusyTimeMs() { return busyTimeMs; }
    public synchronized long getTasksHandled() { return tasksHandled; }
}
