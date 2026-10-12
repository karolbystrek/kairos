package pl.karolbystrek.kairos.api.analytics.infrastructure.worker;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import pl.karolbystrek.kairos.api.analytics.application.OrderAnalyticsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderAnalyticsSchedulerMetricsTests {
    @Test
    void failedCycleIsMeasuredAndNextCycleStillRuns() {
        var analytics = mock(OrderAnalyticsService.class);
        when(analytics.processAvailable()).thenThrow(new IllegalStateException("rollback")).thenReturn(1);
        var meters = new SimpleMeterRegistry();
        try {
            var scheduler = new OrderAnalyticsScheduler(meters, analytics);
            scheduler.process();
            scheduler.process();
            assertThat(meters.get("kairos.worker.failures").tag("worker", "analytics").counter().count()).isEqualTo(1);
            assertThat(meters.get("kairos.worker.cycle.duration").tag("worker", "analytics").timer().count()).isEqualTo(2);
        } finally {
            meters.close();
        }
    }
}
