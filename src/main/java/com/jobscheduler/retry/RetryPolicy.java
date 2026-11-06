package com.jobscheduler.retry;

/**
 * Decides how long to wait before retrying a failed job.
 *
 * [Design Pattern: Strategy] Each job carries its own policy; the engine just asks for a delay.
 * [SOLID: OCP] A new policy (e.g. LinearBackoff) is a new class, with zero changes elsewhere.
 * [SOLID: LSP] Every implementation simply returns a delay >= 0, so the engine treats them all the same.
 */
public interface RetryPolicy {

    /** @param attemptNumber the attempt that just failed (1 = the first run). */
    long nextDelayMs(int attemptNumber);

    String describe();
}
