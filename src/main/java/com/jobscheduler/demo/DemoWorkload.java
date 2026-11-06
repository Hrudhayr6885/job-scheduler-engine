package com.jobscheduler.demo;

import com.jobscheduler.api.JobSchedulerService;
import com.jobscheduler.model.JobRequest;
import com.jobscheduler.model.JobType;
import com.jobscheduler.model.Priority;
import com.jobscheduler.retry.ExponentialBackoffRetry;
import com.jobscheduler.trigger.DelayedTrigger;
import com.jobscheduler.trigger.IntervalTrigger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The scenario from the problem statement:
 *
 *   5,000 invoices  (MEDIUM): 500 customers x 10 invoices each
 *   2,000 emails    (HIGH)
 *     500 reports   (LOW):    each customer's report DEPENDS ON that customer's 10 invoices
 *
 * plus scheduled jobs, a dependency cycle that gets rejected, and a few jobs built to fail
 * so you can watch retries, timeouts, the dead letter queue and skipped jobs.
 */
public final class DemoWorkload {

    public static final int CUSTOMERS = 500;
    public static final int INVOICES_PER_CUSTOMER = 10;
    public static final int EMAILS = 2_000;

    private DemoWorkload() {
    }

    public static void run(JobSchedulerService scheduler) {
        showCycleDetection(scheduler);
        scheduleTimedJobs(scheduler);
        submitInvoicesAndReports(scheduler);
        submitEmails(scheduler);
    }

    /** Job dependencies: A waits for B, then B tries to wait for A, and the engine rejects it. */
    private static void showCycleDetection(JobSchedulerService scheduler) {
        System.out.println("\n▶ Dependency cycle check");
        scheduler.submit(JobRequest.builder("Reconcile ledger", JobType.REPORT)
                .id("CYCLE-A").dependsOn("CYCLE-B").build());
        System.out.println("  Submitted CYCLE-A (depends on CYCLE-B, which doesn't exist yet, so it waits)");
        try {
            scheduler.submit(JobRequest.builder("Close the books", JobType.REPORT)
                    .id("CYCLE-B").dependsOn("CYCLE-A").build());
        } catch (IllegalArgumentException e) {
            System.out.println("  ✔ Rejected CYCLE-B: " + e.getMessage());
        }
        scheduler.cancel("CYCLE-A");
        System.out.println("  Cancelled CYCLE-A (the job it waits for will never exist)");
    }

    /** Scheduled execution: one recurring job and one delayed job. */
    private static void scheduleTimedJobs(JobSchedulerService scheduler) {
        System.out.println("\n▶ Scheduled jobs");
        scheduler.submit(JobRequest.builder("Hourly sales summary", JobType.REPORT)
                .priority(Priority.HIGH)
                .trigger(new IntervalTrigger(Duration.ofSeconds(8), 5)) // "hourly" sped up for the demo
                .build());
        System.out.println("  'Hourly sales summary': every 8s, 5 runs");

        scheduler.submit(JobRequest.builder("Payment reminder to finance", JobType.EMAIL)
                .priority(Priority.CRITICAL)
                .payload("to=finance@example.com")
                .trigger(new DelayedTrigger(Duration.ofSeconds(12)))
                .build());
        System.out.println("  'Payment reminder to finance': once, in 12s");
    }

    private static void submitInvoicesAndReports(JobSchedulerService scheduler) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int c = 1; c <= CUSTOMERS; c++) {
            String customer = String.format("C%03d", c);
            List<String> invoiceIds = new ArrayList<>();

            for (int k = 1; k <= INVOICES_PER_CUSTOMER; k++) {
                String invoiceId = String.format("INV-%s-%02d", customer, k);
                // Every 100th customer has one broken invoice: it goes to the DLQ and their report gets SKIPPED
                double amount = (c % 100 == 0 && k == 1) ? -50 : random.nextInt(100, 5_000);
                scheduler.submit(JobRequest.builder("Invoice " + invoiceId, JobType.INVOICE)
                        .id(invoiceId)
                        .priority(Priority.MEDIUM)
                        .payload("customer=" + customer + ";amount=" + amount)
                        .maxRetries(3)
                        .build());
                invoiceIds.add(invoiceId);
            }

            scheduler.submit(JobRequest.builder("Monthly report " + customer, JobType.REPORT)
                    .id("REP-" + customer)
                    .priority(Priority.LOW)
                    .dependsOn(invoiceIds)       // waits for all 10 invoices of this customer
                    .timeoutMs(1_000)            // reports that hang get killed after 1s
                    .maxRetries(2)
                    .build());
        }
        System.out.println("\n▶ Submitted " + CUSTOMERS * INVOICES_PER_CUSTOMER + " invoices (MEDIUM) and "
                + CUSTOMERS + " reports (LOW, waiting for their invoices)");
    }

    private static void submitEmails(JobSchedulerService scheduler) {
        for (int n = 1; n <= EMAILS; n++) {
            // every 400th address bounces permanently, so after all retries it lands in the DLQ
            String to = (n % 400 == 0) ? "bounce" + n + "@example.com" : String.format("user%04d@example.com", n);
            scheduler.submit(JobRequest.builder("Email notification #" + n, JobType.EMAIL)
                    .id(String.format("EMAIL-%04d", n))
                    .priority(Priority.HIGH)
                    .payload("to=" + to)
                    .retryPolicy(new ExponentialBackoffRetry(300, 3_000))
                    .maxRetries(3)
                    .build());
        }
        System.out.println("▶ Submitted " + EMAILS + " emails (HIGH): submitted last, but they jump ahead of the invoices\n");
    }
}
