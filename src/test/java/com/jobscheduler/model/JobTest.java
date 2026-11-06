package com.jobscheduler.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobTest {

    private static Job newJob(int maxRetries) {
        return new Job("J1", JobRequest.builder("test", JobType.INVOICE).maxRetries(maxRetries).build(), 0);
    }

    @Test
    void followsTheHappyPath() {
        Job job = newJob(3);
        assertEquals(JobStatus.WAITING, job.getStatus());
        assertTrue(job.markQueued(1));
        assertTrue(job.startAttempt("W01", 2));
        job.markSucceeded(3);
        assertEquals(JobStatus.SUCCEEDED, job.getStatus());
        assertEquals(1, job.getAttempts());
    }

    @Test
    void rejectsIllegalTransitions() {
        Job job = newJob(3);
        assertFalse(job.startAttempt("W01", 1), "cannot run a job that was never queued");
        assertThrows(IllegalStateException.class, () -> job.markSucceeded(1));
    }

    @Test
    void finishedJobCannotBeCancelled() {
        Job job = newJob(3);
        job.markQueued(1);
        job.startAttempt("W01", 2);
        job.markSucceeded(3);
        assertFalse(job.cancel(4));
    }

    @Test
    void countsRetriesCorrectly() {
        Job job = newJob(1); // 1 retry = 2 attempts in total
        job.markQueued(1);
        job.startAttempt("W01", 2);
        assertTrue(job.hasRetriesLeft());
        job.markRetryScheduled("boom");
        job.markQueued(3);
        job.startAttempt("W01", 4);
        assertFalse(job.hasRetriesLeft());
    }

    @Test
    void builderRejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> JobRequest.builder("x", JobType.EMAIL).maxRetries(-1).build());
        assertThrows(IllegalArgumentException.class, () -> JobRequest.builder("x", JobType.EMAIL).timeoutMs(0).build());
    }
}
