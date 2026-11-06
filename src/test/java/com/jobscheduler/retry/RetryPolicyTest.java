package com.jobscheduler.retry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    @Test
    void exponentialBackoffDoublesAndIsCapped() {
        ExponentialBackoffRetry policy = new ExponentialBackoffRetry(100, 1_000);
        assertBetween(policy.nextDelayMs(1), 100, 110);   // base
        assertBetween(policy.nextDelayMs(2), 200, 220);   // 2x
        assertBetween(policy.nextDelayMs(3), 400, 440);   // 4x
        assertBetween(policy.nextDelayMs(10), 1_000, 1_100); // capped at max (+ jitter)
    }

    @Test
    void fixedDelayIsAlwaysTheSame() {
        FixedDelayRetry policy = new FixedDelayRetry(250);
        assertEquals(250, policy.nextDelayMs(1));
        assertEquals(250, policy.nextDelayMs(7));
    }

    private static void assertBetween(long value, long min, long max) {
        assertTrue(value >= min && value <= max, value + " should be between " + min + " and " + max);
    }
}
