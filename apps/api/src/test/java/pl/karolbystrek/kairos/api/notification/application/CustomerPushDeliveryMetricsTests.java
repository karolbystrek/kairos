package pl.karolbystrek.kairos.api.notification.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.karolbystrek.kairos.api.notification.application.model.ClaimedCustomerPushDelivery;
import pl.karolbystrek.kairos.api.notification.application.model.WebPushResult;
import pl.karolbystrek.kairos.api.notification.application.port.WebPushSender;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerPushDeliveryMetricsTests {
    @ParameterizedTest
    @CsvSource({"201,true,accepted", "410,true,invalid_subscription", "503,true,transient_failure",
            "400,true,failed", "201,false,stale"})
    void countsOnlyBoundedTransportOutcomes(int status, boolean completed, String outcome) {
        var sender = mock(WebPushSender.class);
        var completion = mock(CustomerPushDeliveryCompletionService.class);
        var result = new WebPushResult(status, null, "arbitrary-private-diagnostic", "private-endpoint");
        when(sender.send(any())).thenReturn(result);
        when(completion.complete(any(), any(), any(), any(), any())).thenReturn(completed);
        var now = Instant.parse("2026-10-12T12:00:00Z");
        var id = UUID.randomUUID();
        var delivery = new ClaimedCustomerPushDelivery(id, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "https://private.example/secret", new byte[]{1}, new byte[]{2}, "{}", now.plusSeconds(60));
        var meters = new SimpleMeterRegistry();
        try {
            var processor = new CustomerPushDeliveryProcessor(meters, sender, completion, Clock.fixed(now, ZoneOffset.UTC));
            processor.process(delivery);
            assertThat(meters.get("kairos.delivery.attempts").tags("channel", "push", "outcome", outcome)
                    .counter().count()).isEqualTo(1);
            assertThat(meters.get("kairos.delivery.attempt.duration").tag("channel", "push").timer().count()).isEqualTo(1);
            assertThat(meters.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags().toString())
                    .doesNotContain(id.toString(), "private", "secret", "arbitrary"));
        } finally {
            meters.close();
        }
    }
}
