package com.library.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/** A {@link Clock} whose instant tests can set or advance; thread-safe so concurrency tests can share it. */
public class MutableClock extends Clock {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("UTC");

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    public MutableClock(Instant start, ZoneId zone) {
        this.instant = new AtomicReference<>(start);
        this.zone = zone;
    }

    /** Midday on the given date in {@link #DEFAULT_ZONE}. */
    public static MutableClock at(LocalDate date) {
        return new MutableClock(date.atTime(12, 0).atZone(DEFAULT_ZONE).toInstant(), DEFAULT_ZONE);
    }

    public void setDate(LocalDate date) {
        instant.set(date.atTime(12, 0).atZone(zone).toInstant());
    }

    public void advance(Duration duration) {
        instant.updateAndGet(i -> i.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(instant.get(), newZone);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
