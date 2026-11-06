package com.jobscheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock whose time we control in tests. This is why the engine takes a Clock in its constructor (DIP). */
public class TestClock extends Clock {

    private long millis;

    public TestClock(long startMillis) {
        this.millis = startMillis;
    }

    public void advance(long ms) {
        millis += ms;
    }

    @Override
    public long millis() {
        return millis;
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
