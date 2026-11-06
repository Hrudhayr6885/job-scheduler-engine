package com.jobscheduler.dependency;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds cycles in the dependency graph with Depth-First Search (DFS).
 *
 * If A waits for B and B waits for A, both wait forever, so such a job is rejected
 * when it is submitted.
 */
public final class CycleDetector {

    private CycleDetector() {
    }

    /**
     * Would adding "newJobId depends on parents" create a cycle?
     *
     * We walk upwards from each parent through ITS parents. If we ever reach newJobId,
     * then newJobId is its own ancestor, which means there is a cycle.
     *
     * @param parentsOf existing graph: jobId → ids it depends on
     */
    public static boolean createsCycle(String newJobId, List<String> parents, Map<String, List<String>> parentsOf) {
        Deque<String> stack = new ArrayDeque<>(parents);
        Set<String> visited = new HashSet<>();
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (current.equals(newJobId)) return true;
            if (visited.add(current)) {
                stack.addAll(parentsOf.getOrDefault(current, List.of()));
            }
        }
        return false;
    }
}
