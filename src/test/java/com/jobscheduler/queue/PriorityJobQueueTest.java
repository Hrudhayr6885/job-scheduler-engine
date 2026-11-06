package com.jobscheduler.queue;

import com.jobscheduler.TestClock;
import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobRequest;
import com.jobscheduler.model.JobType;
import com.jobscheduler.model.Priority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PriorityJobQueueTest {

    private final TestClock clock = new TestClock(1_000_000);
    private final PriorityJobQueue queue = new PriorityJobQueue(clock, 5_000);

    private static Job job(String id, Priority priority) {
        return new Job(id, JobRequest.builder(id, JobType.EMAIL).priority(priority).build(), 0);
    }

    @Test
    void higherPriorityComesOutFirst() throws InterruptedException {
        queue.enqueue(job("low", Priority.LOW));
        queue.enqueue(job("critical", Priority.CRITICAL));
        queue.enqueue(job("medium", Priority.MEDIUM));
        queue.enqueue(job("high", Priority.HIGH));

        assertEquals("critical", queue.take().getId());
        assertEquals("high", queue.take().getId());
        assertEquals("medium", queue.take().getId());
        assertEquals("low", queue.take().getId());
    }

    @Test
    void samePriorityIsFirstComeFirstServed() throws InterruptedException {
        queue.enqueue(job("first", Priority.MEDIUM));
        queue.enqueue(job("second", Priority.MEDIUM));

        assertEquals("first", queue.take().getId());
        assertEquals("second", queue.take().getId());
    }

    @Test
    void agingLetsAnOldLowJobBeatANewHighJob() throws InterruptedException {
        queue.enqueue(job("old-low", Priority.LOW));  // weight 1
        clock.advance(15_000);                        // waited 3 aging steps → effective 4
        queue.enqueue(job("new-high", Priority.HIGH)); // weight 3

        assertEquals("old-low", queue.take().getId());
    }

    @Test
    void cancelledJobCannotBeQueued() {
        Job job = job("x", Priority.HIGH);
        job.cancel(0);
        assertFalse(queue.enqueue(job));
        assertEquals(0, queue.size());
    }
}
