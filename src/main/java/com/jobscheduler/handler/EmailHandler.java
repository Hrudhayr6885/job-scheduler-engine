package com.jobscheduler.handler;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobType;

/**
 * Sends one email notification. Payload example: "to=user0001@example.com"
 *
 * Simulated failures:
 *   - address without "@"         → NonRetryableException
 *   - address starting "bounce"   → always fails, so it ends up in the dead letter queue after all retries
 *   - 5% "SMTP server busy"       → temporary, usually succeeds on retry
 */
public class EmailHandler extends AbstractJobHandler {

    @Override
    public JobType supports() {
        return JobType.EMAIL;
    }

    @Override
    protected void validate(Job job) throws NonRetryableException {
        String to = payloadValue(job, "to");
        if (to == null || !to.contains("@")) {
            throw new NonRetryableException("Invalid email address: " + to);
        }
    }

    @Override
    protected void execute(Job job) throws Exception {
        simulateWork(10, 30);
        String to = payloadValue(job, "to");
        if (to.startsWith("bounce")) {
            throw new RuntimeException("550 Mailbox unavailable: " + to);
        }
        if (chance(0.05)) {
            throw new RuntimeException("421 SMTP server busy, try again later");
        }
    }
}
