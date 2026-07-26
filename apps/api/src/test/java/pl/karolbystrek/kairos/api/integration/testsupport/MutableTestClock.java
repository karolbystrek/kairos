package pl.karolbystrek.kairos.api.integration.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableTestClock extends Clock {

    private final AtomicReference<Instant> instant =
            new AtomicReference<>(Instant.parse("2026-07-26T12:00:00Z"));

    public void setInstant(Instant value) {
        instant.set(value);
    }

    public void advance(java.time.Duration duration) {
        instant.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant(), zone);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
