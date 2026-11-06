package com.jobscheduler.dashboard;

import com.jobscheduler.api.JobSchedulerService;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.util.NamedThreadFactory;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Prints a one-line status to the terminal every couple of seconds (only when something changed).
 *
 * [SOLID: SRP] Only displays. It reads from the facade and never changes engine state.
 */
public class ConsoleDashboard {

    private final JobSchedulerService scheduler;
    private final ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("console-dashboard"));
    private String lastLine = "";

    public ConsoleDashboard(JobSchedulerService scheduler) {
        this.scheduler = scheduler;
    }

    public void start(long periodMs) {
        timer.scheduleAtFixedRate(this::print, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    public void print() {
        Map<JobStatus, Long> c = scheduler.statusCounts();
        String line = String.format(
                "Queued %5d | Waiting %4d | Running %2d | Retrying %3d | Succeeded %5d | Dead %2d | Skipped %2d | Workers busy %d/%d",
                c.get(JobStatus.QUEUED), c.get(JobStatus.WAITING), c.get(JobStatus.RUNNING),
                c.get(JobStatus.RETRY_SCHEDULED), c.get(JobStatus.SUCCEEDED), c.get(JobStatus.DEAD),
                c.get(JobStatus.SKIPPED), scheduler.getWorkerPool().busyCount(), scheduler.getWorkerPool().size());
        if (line.equals(lastLine)) return;
        lastLine = line;
        System.out.printf("[%6.1fs] %s%n", scheduler.getMetrics().uptimeMs() / 1000.0, line);
    }

    public void stop() {
        timer.shutdownNow();
    }
}
