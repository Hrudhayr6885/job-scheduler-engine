package com.jobscheduler.model;

/**
 * How urgent a job is. Higher weight = runs earlier.
 *
 * [OOP: Encapsulation] Each constant carries its own weight, so the "what is more important"
 *   rule lives in one place instead of being scattered as magic numbers.
 */
public enum Priority {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
