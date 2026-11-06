package com.jobscheduler.trigger;

import com.jobscheduler.model.JobRequest;
import com.jobscheduler.util.NamedThreadFactory;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Fires delayed and recurring jobs at the right time.
 * Each time a schedule fires, a fresh one-off job ("Sales summary #3") is submitted.
 *
 * [SOLID: SRP] Only cares about time. What happens to the job afterwards is the engine's business.
 * [SOLID: DIP] Submits through a plain Consumer, so it doesn't depend on JobSchedulerService.
 */
public class TriggerScheduler {

    /** One registered schedule, shown on the dashboard. */
    public static final class ScheduledJob {
        private final JobRequest template;
        private int runs;
        private long nextFireAt;
        private boolean active = true;

        private ScheduledJob(JobRequest template) {
            this.template = template;
        }

        public String getName() { return template.getName(); }
        public String getTriggerDescription() { return template.getTrigger().describe(); }
        public synchronized int getRuns() { return runs; }
        public synchronized long getNextFireAt() { return nextFireAt; }
        public synchronized boolean isActive() { return active; }
    }

    private final Consumer<JobRequest> submitter;
    private final Clock clock;
    private final ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("trigger-timer"));
    private final List<ScheduledJob> scheduledJobs = new CopyOnWriteArrayList<>();

    public TriggerScheduler(Consumer<JobRequest> submitter, Clock clock) {
        this.submitter = submitter;
        this.clock = clock;
    }

    public void schedule(JobRequest template) {
        ScheduledJob scheduledJob = new ScheduledJob(template);
        scheduledJobs.add(scheduledJob);
        planNextRun(scheduledJob);
    }

    private void planNextRun(ScheduledJob sj) {
        long now = clock.millis();
        synchronized (sj) {
            Optional<Long> next = sj.template.getTrigger().nextFireTime(now, sj.runs);
            if (next.isEmpty()) {
                sj.active = false;
                return;
            }
            sj.nextFireAt = next.get();
        }
        timer.schedule(() -> fire(sj), Math.max(0, sj.getNextFireAt() - now), TimeUnit.MILLISECONDS);
    }

    private void fire(ScheduledJob sj) {
        int run;
        synchronized (sj) {
            run = ++sj.runs;
        }
        try {
            submitter.accept(sj.template.asOneOffCopy(sj.template.getName() + " #" + run));
        } catch (RuntimeException e) {
            System.err.println("[Trigger] could not submit " + sj.getName() + ": " + e.getMessage());
        }
        planNextRun(sj);
    }

    public boolean hasActiveSchedules() {
        return scheduledJobs.stream().anyMatch(ScheduledJob::isActive);
    }

    public List<ScheduledJob> getScheduledJobs() {
        return List.copyOf(scheduledJobs);
    }

    public void shutdown() {
        timer.shutdownNow();
    }
}
