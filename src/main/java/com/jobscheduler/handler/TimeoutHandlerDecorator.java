package com.jobscheduler.handler;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobType;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Wraps ANY handler and enforces the job's timeout.
 *
 *   new TimeoutHandlerDecorator(new InvoiceHandler(), threads)
 *
 * [Design Pattern: Decorator] Same interface as the handler it wraps, plus one extra behaviour.
 *   InvoiceHandler stays clean: it doesn't even know timeouts exist.
 * [SOLID: SRP] Timeout logic lives in exactly one place.
 * [SOLID: OCP] More decorators (logging, metrics, rate limiting) can be stacked later
 *   without editing a single handler.
 */
public class TimeoutHandlerDecorator implements JobHandler {

    private final JobHandler inner;
    private final ExecutorService threads;

    public TimeoutHandlerDecorator(JobHandler inner, ExecutorService threads) {
        this.inner = inner;
        this.threads = threads;
    }

    @Override
    public JobType supports() {
        return inner.supports();
    }

    @Override
    public void handle(Job job) throws Exception {
        Future<Object> future = threads.submit(() -> {
            inner.handle(job);
            return null;
        });
        try {
            future.get(job.getTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true); // interrupt the stuck thread so it stops working
            throw new JobTimeoutException("Timed out after " + job.getTimeoutMs() + " ms");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause(); // unwrap: report the handler's real error
            if (cause instanceof Exception exception) throw exception;
            throw new RuntimeException(cause);
        }
    }
}
