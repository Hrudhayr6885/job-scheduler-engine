package com.jobscheduler.handler;

import com.jobscheduler.model.Job;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared skeleton for all handlers.
 *
 * [Design Pattern: Template Method] handle() fixes the order of the steps (validate → execute).
 *   It is final, so no subclass can skip validation by accident. Subclasses only fill in the steps.
 * [OOP: Inheritance] Invoice/Email/Report handlers reuse the payload parsing and simulation helpers.
 */
public abstract class AbstractJobHandler implements JobHandler {

    @Override
    public final void handle(Job job) throws Exception {
        validate(job);  // step 1: bad input → NonRetryableException (retrying won't fix it)
        execute(job);   // step 2: the real work
    }

    /** Hook: override to check the payload. Default = nothing to check. */
    protected void validate(Job job) throws NonRetryableException {
    }

    protected abstract void execute(Job job) throws Exception;

    // ---------------------------------------------------------------- helpers for subclasses

    /** Reads a value from a "key=value;key=value" payload. */
    protected String payloadValue(Job job, String key) {
        for (String part : job.getPayload().split(";")) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length == 2 && keyValue[0].trim().equals(key)) {
                return keyValue[1].trim();
            }
        }
        return null;
    }

    /** Pretends to do work (real life: database calls, PDF rendering, talking to an SMTP server...). */
    protected void simulateWork(int minMs, int maxMs) throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs + 1));
    }

    protected boolean chance(double probability) {
        return ThreadLocalRandom.current().nextDouble() < probability;
    }
}
