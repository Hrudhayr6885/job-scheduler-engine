package com.jobscheduler.dependency;

import com.jobscheduler.events.JobEvent;
import com.jobscheduler.events.JobEventListener;
import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Tracks "job C must wait for A and B" relationships.
 *
 * [SOLID: SRP] Only tracks dependencies. Running jobs is someone else's job.
 * [Design Pattern: Observer] Listens to the EventBus:
 *   - parent SUCCEEDED                → children whose parents are all done become ready
 *   - parent DEAD/SKIPPED/CANCELLED   → its children are skipped (and their children, and so on)
 */
public class DependencyManager implements JobEventListener {

    public enum Decision { READY, WAITING, SKIP }

    private final Function<String, Job> jobLookup;
    private final Map<String, List<String>> parentsOf = new HashMap<>();    // child  → all parents (graph for cycle check)
    private final Map<String, Set<String>> pendingParents = new HashMap<>(); // child  → parents not finished yet
    private final Map<String, Set<String>> childrenOf = new HashMap<>();     // parent → children waiting on it

    private Consumer<Job> onReady = job -> { };
    private Consumer<Job> onSkip = job -> { };

    public DependencyManager(Function<String, Job> jobLookup) {
        this.jobLookup = jobLookup;
    }

    public void setCallbacks(Consumer<Job> onReady, Consumer<Job> onSkip) {
        this.onReady = onReady;
        this.onSkip = onSkip;
    }

    /**
     * Called once when a job is submitted.
     *
     * @throws IllegalArgumentException if the job would create a dependency cycle
     */
    public synchronized Decision register(Job job) {
        List<String> parents = job.getDependsOn();
        if (parents.isEmpty()) return Decision.READY;

        if (CycleDetector.createsCycle(job.getId(), parents, parentsOf)) {
            throw new IllegalArgumentException("Cycle detected: " + job.getId() + " -> " + parents + " would wait forever");
        }
        parentsOf.put(job.getId(), parents);

        Set<String> pending = new HashSet<>();
        for (String parentId : parents) {
            Job parent = jobLookup.apply(parentId);
            JobStatus status = parent == null ? null : parent.getStatus();
            if (status == JobStatus.SUCCEEDED) continue;              // already done
            if (status != null && status.isTerminal()) return Decision.SKIP; // already failed
            pending.add(parentId);                                    // still running, or not submitted yet
        }
        if (pending.isEmpty()) return Decision.READY;

        pendingParents.put(job.getId(), pending);
        for (String parentId : pending) {
            childrenOf.computeIfAbsent(parentId, k -> new HashSet<>()).add(job.getId());
        }
        return Decision.WAITING;
    }

    @Override
    public synchronized void onEvent(JobEvent event) {
        switch (event.type()) {
            case JOB_SUCCEEDED -> releaseChildren(event.job().getId());
            case JOB_DEAD, JOB_SKIPPED, JOB_CANCELLED -> skipChildren(event.job().getId());
            default -> { }
        }
    }

    private void releaseChildren(String parentId) {
        Set<String> children = childrenOf.remove(parentId);
        if (children == null) return;
        for (String childId : children) {
            Set<String> pending = pendingParents.get(childId);
            if (pending == null) continue;
            pending.remove(parentId);
            if (pending.isEmpty()) {
                pendingParents.remove(childId);
                Job child = jobLookup.apply(childId);
                if (child != null) onReady.accept(child);
            }
        }
    }

    private void skipChildren(String parentId) {
        Set<String> children = childrenOf.remove(parentId);
        if (children == null) return;
        for (String childId : children) {
            pendingParents.remove(childId);
            Job child = jobLookup.apply(childId);
            if (child != null) onSkip.accept(child); // publishes JOB_SKIPPED, which skips grandchildren too
        }
    }
}
