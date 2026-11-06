package com.jobscheduler.handler;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobType;

/**
 * Generates one report (slowest job type).
 *
 * Simulated failure:
 *   - 2% of the time the "database query" hangs for 10s, so the timeout kills it and it is retried.
 */
public class ReportHandler extends AbstractJobHandler {

    @Override
    public JobType supports() {
        return JobType.REPORT;
    }

    @Override
    protected void execute(Job job) throws Exception {
        if (chance(0.02)) {
            Thread.sleep(10_000); // stuck query; TimeoutHandlerDecorator will interrupt this
        }
        simulateWork(80, 200);
    }
}
