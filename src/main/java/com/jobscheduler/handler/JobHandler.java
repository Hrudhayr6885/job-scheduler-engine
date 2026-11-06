package com.jobscheduler.handler;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobType;

/**
 * The business logic for one job type.
 *
 * [OOP: Abstraction] The engine only knows "handle(job)". It has no idea what an invoice or an email is.
 * [OOP: Polymorphism] Same call, different behaviour (InvoiceHandler, EmailHandler, ReportHandler).
 * [SOLID: OCP] New job type (SMS, PDF export, ...) = one new class + one register() call.
 * [SOLID: ISP] Just two small methods, nothing a handler doesn't need.
 */
public interface JobHandler {

    JobType supports();

    /** Throw any exception to signal failure; throw NonRetryableException if retrying can't help. */
    void handle(Job job) throws Exception;
}
