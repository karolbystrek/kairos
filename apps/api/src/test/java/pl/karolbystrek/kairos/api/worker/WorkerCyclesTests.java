package pl.karolbystrek.kairos.api.worker;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerCyclesTests {

    @Test
    void failedCycleIsMeasuredCountedAndReportedAsNoWork() {
        var meters = new SimpleMeterRegistry();
        try {
            var cycles = new WorkerCycles(meters);

            assertThat(cycles.run("analytics", "failed", () -> {
                throw new IllegalStateException("rollback");
            })).isFalse();
            assertThat(cycles.run("analytics", "failed", () -> true)).isTrue();

            assertThat(meters.get("kairos.worker.failures").tag("worker", "analytics").counter().count()).isEqualTo(1);
            assertThat(meters.get("kairos.worker.cycle.duration").tag("worker", "analytics").timer().count()).isEqualTo(2);
        } finally {
            meters.close();
        }
    }
}
