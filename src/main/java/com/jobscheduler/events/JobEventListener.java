package com.jobscheduler.events;

/**
 * Anything that wants to react to job events (history, metrics, dependency tracking...).
 *
 * [SOLID: ISP] One tiny method, so listeners implement only what they actually use.
 */
@FunctionalInterface
public interface JobEventListener {

    void onEvent(JobEvent event);
}
