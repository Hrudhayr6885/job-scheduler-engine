package com.jobscheduler.handler;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobType;

/**
 * Processes one invoice. Payload example: "customer=C001;amount=1520.50"
 *
 * Simulated failures:
 *   - amount <= 0           → NonRetryableException (bad data, goes straight to the DLQ)
 *   - 3% "gateway timeout"  → temporary, the engine retries it
 */
public class InvoiceHandler extends AbstractJobHandler {

    @Override
    public JobType supports() {
        return JobType.INVOICE;
    }

    @Override
    protected void validate(Job job) throws NonRetryableException {
        String amount = payloadValue(job, "amount");
        double value;
        try {
            value = Double.parseDouble(amount);
        } catch (NumberFormatException | NullPointerException e) {
            throw new NonRetryableException("Invoice amount is missing or not a number: " + amount);
        }
        if (value <= 0) {
            throw new NonRetryableException("Invalid invoice amount: " + amount);
        }
    }

    @Override
    protected void execute(Job job) throws Exception {
        simulateWork(20, 60);
        if (chance(0.03)) {
            throw new RuntimeException("Payment gateway timeout (temporary)");
        }
    }
}
