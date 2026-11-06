package com.jobscheduler.dependency;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CycleDetectorTest {

    @Test
    void detectsDirectCycle() {
        Map<String, List<String>> graph = Map.of("A", List.of("B")); // A depends on B
        assertTrue(CycleDetector.createsCycle("B", List.of("A"), graph)); // B depends on A → cycle
    }

    @Test
    void detectsLongCycle() {
        Map<String, List<String>> graph = Map.of("A", List.of("B"), "B", List.of("C"));
        assertTrue(CycleDetector.createsCycle("C", List.of("A"), graph)); // A→B→C→A
    }

    @Test
    void detectsSelfDependency() {
        assertTrue(CycleDetector.createsCycle("A", List.of("A"), Map.of()));
    }

    @Test
    void allowsDiamondShape() {
        // D depends on B and C, both depend on A: no cycle
        Map<String, List<String>> graph = Map.of("B", List.of("A"), "C", List.of("A"));
        assertFalse(CycleDetector.createsCycle("D", List.of("B", "C"), graph));
    }
}
