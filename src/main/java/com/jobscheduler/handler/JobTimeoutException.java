package com.jobscheduler.handler;

/** Thrown by TimeoutHandlerDecorator when a job runs longer than its timeout. */
public class JobTimeoutException extends Exception {

    public JobTimeoutException(String message) {
        super(message);
    }
}
