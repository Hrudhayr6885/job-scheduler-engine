package com.jobscheduler.handler;

/**
 * Thrown when retrying is pointless (bad input, e.g. a negative invoice amount).
 * The engine sends the job straight to the dead letter queue instead of retrying it.
 */
public class NonRetryableException extends Exception {

    public NonRetryableException(String message) {
        super(message);
    }
}
