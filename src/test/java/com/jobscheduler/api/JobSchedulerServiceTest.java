package com.jobscheduler.api;

import com.jobscheduler.handler.JobHandler;
import com.jobscheduler.handler.JobHandlerRegistry;
import com.jobscheduler.handler.NonRetryableException;
import com.jobscheduler.handler.TimeoutHandlerDecorator;
import com.jobscheduler.history.InMemoryExecutionHistoryRepository;
import com.jobscheduler.model.AttemptStatus;
import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobExecution;
import com.jobscheduler.model.JobRequest;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.JobType;
import com.jobscheduler.retry.FixedDelayRetry;
import com.jobscheduler.worker.RoundRobinStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** End-to-end tests: real engine, fake handler, in-memory history. */
class JobSchedulerServiceTest {

    /** Behaves according to its payload: "ok", "fail", "bad-input" or "hang". */
    static class ScriptedHandler implements JobHandler {
        @Override
        public JobType supports() {
            return JobType.INVOICE;
        }

        @Override
        public void handle(Job job) throws Exception {
            switch (job.getPayload()) {
                case "fail" -> throw new RuntimeException("always fails");
                case "bad-input" -> throw new NonRetryableException("bad input");
                case "hang" -> Thread.sleep(5_000);
                default -> Thread.sleep(5);
            }
        }
    }

    private ExecutorService handlerThreads;
    private InMemoryExecutionHistoryRepository history;
    private JobSchedulerService scheduler;

    @BeforeEach
    void setUp() {
        handlerThreads = Executors.newCachedThreadPool();
        history = new InMemoryExecutionHistoryRepository();
        JobHandlerRegistry registry = new JobHandlerRegistry()
                .register(new TimeoutHandlerDecorator(new ScriptedHandler(), handlerThreads));
        scheduler = new JobSchedulerService(new SchedulerConfig(2, 5_000), registry, history,
                new RoundRobinStrategy(), Clock.systemUTC());
        scheduler.start();
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
        handlerThreads.shutdownNow();
    }

    private JobRequest.Builder invoice(String id, String behaviour) {
        return JobRequest.builder(id, JobType.INVOICE).id(id).payload(behaviour).retryPolicy(new FixedDelayRetry(10));
    }

    private Job awaitFinished(String id) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            Job job = scheduler.getJob(id).orElseThrow();
            if (job.getStatus().isTerminal()) return job;
            Thread.sleep(10);
        }
        fail("Job " + id + " did not finish in time");
        return null;
    }

    @Test
    void successfulJobSucceeds() throws InterruptedException {
        scheduler.submit(invoice("ok-1", "ok").build());
        assertEquals(JobStatus.SUCCEEDED, awaitFinished("ok-1").getStatus());
        assertEquals(1, history.findByJobId("ok-1").size());
    }

    @Test
    void failingJobIsRetriedThenMovedToDeadLetterQueue() throws InterruptedException {
        scheduler.submit(invoice("fail-1", "fail").maxRetries(2).build());

        Job job = awaitFinished("fail-1");
        assertEquals(JobStatus.DEAD, job.getStatus());
        assertEquals(3, job.getAttempts(), "1 run + 2 retries");
        assertEquals(1, scheduler.getDeadJobs().size());
        assertEquals(3, history.findByJobId("fail-1").size());
    }

    @Test
    void nonRetryableErrorSkipsRetries() throws InterruptedException {
        scheduler.submit(invoice("bad-1", "bad-input").maxRetries(5).build());
        Job job = awaitFinished("bad-1");
        assertEquals(JobStatus.DEAD, job.getStatus());
        assertEquals(1, job.getAttempts());
    }

    @Test
    void slowJobTimesOut() throws InterruptedException {
        scheduler.submit(invoice("hang-1", "hang").timeoutMs(100).maxRetries(0).build());
        assertEquals(JobStatus.DEAD, awaitFinished("hang-1").getStatus());
        List<JobExecution> attempts = history.findByJobId("hang-1");
        assertEquals(AttemptStatus.TIMED_OUT, attempts.get(0).status());
    }

    @Test
    void childRunsOnlyAfterParentSucceeds() throws InterruptedException {
        scheduler.submit(invoice("child", "ok").dependsOn("parent").build()); // parent not submitted yet
        Thread.sleep(50);
        assertEquals(JobStatus.WAITING, scheduler.getJob("child").orElseThrow().getStatus());

        scheduler.submit(invoice("parent", "ok").build());
        Job parent = awaitFinished("parent");
        Job child = awaitFinished("child");
        assertEquals(JobStatus.SUCCEEDED, child.getStatus());
        assertTrue(child.getStartedAt() >= parent.getFinishedAt());
    }

    @Test
    void childIsSkippedWhenParentDies() throws InterruptedException {
        scheduler.submit(invoice("bad-parent", "bad-input").build());
        scheduler.submit(invoice("child-of-bad", "ok").dependsOn("bad-parent").build());
        scheduler.submit(invoice("grandchild", "ok").dependsOn("child-of-bad").build());

        assertEquals(JobStatus.SKIPPED, awaitFinished("child-of-bad").getStatus());
        assertEquals(JobStatus.SKIPPED, awaitFinished("grandchild").getStatus());
    }

    @Test
    void dependencyCycleIsRejected() {
        scheduler.submit(invoice("A", "ok").dependsOn("B").build());
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.submit(invoice("B", "ok").dependsOn("A").build()));
    }

    @Test
    void waitingJobCanBeCancelled() {
        scheduler.submit(invoice("lonely", "ok").dependsOn("never-submitted").build());
        assertTrue(scheduler.cancel("lonely"));
        assertEquals(JobStatus.CANCELLED, scheduler.getJob("lonely").orElseThrow().getStatus());
    }
}
