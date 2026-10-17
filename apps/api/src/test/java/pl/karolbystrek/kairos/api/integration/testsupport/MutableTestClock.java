package pl.karolbystrek.kairos.api.integration.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableTestClock extends Clock {

    // Null means the clock follows system time, so tests that never pin it behave as under the real clock.
    private final AtomicReference<Instant> instant = new AtomicReference<>();

    public void setInstant(Instant value) {
        instant.set(value);
    }

    public void useSystemTime() {
        instant.set(null);
    }

    public void advance(Duration duration) {
        instant.updateAndGet(current -> (current == null ? Instant.now() : current).plus(duration));
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
        var pinned = instant.get();
        return pinned == null ? Instant.now() : pinned;
    }
}
