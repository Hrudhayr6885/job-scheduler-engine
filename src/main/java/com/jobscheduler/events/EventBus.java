package com.jobscheduler.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A tiny publish/subscribe hub.
 *
 * [Design Pattern: Observer] The engine publishes events; history, metrics and the dependency
 *   manager subscribe. The engine doesn't know (or care) who is listening.
 * [SOLID: DIP] Publishers depend on the JobEventListener abstraction, not on concrete classes.
 * [SOLID: OCP] Want Slack alerts when a job dies? Subscribe a new listener. No engine changes.
 */
public class EventBus {

    private final List<JobEventListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(JobEventListener listener) {
        listeners.add(listener);
    }

    public void publish(JobEvent event) {
        for (JobEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                // one broken listener must never break the engine or the other listeners
                System.err.println("[EventBus] " + listener.getClass().getSimpleName() + " failed: " + e.getMessage());
            }
        }
    }
}
