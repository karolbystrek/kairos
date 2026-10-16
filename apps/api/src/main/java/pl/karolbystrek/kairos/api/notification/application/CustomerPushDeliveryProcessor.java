package pl.karolbystrek.kairos.api.notification.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.karolbystrek.kairos.api.notification.application.model.ClaimedCustomerPushDelivery;
import pl.karolbystrek.kairos.api.notification.application.model.WebPushMessage;
import pl.karolbystrek.kairos.api.notification.application.model.WebPushResult;
import pl.karolbystrek.kairos.api.notification.application.port.WebPushSender;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerPushDeliveryProcessor {

    private final MeterRegistry meters;
    private final WebPushSender sender;
    private final CustomerPushDeliveryCompletionService completionService;
    private final Clock clock;

    public void process(ClaimedCustomerPushDelivery delivery) {
        var sample = Timer.start(meters);
        try {
            WebPushResult result;
            try {
                result = sender.send(new WebPushMessage(
                        delivery.orderId(),
                        delivery.endpoint(),
                        delivery.p256dhKey(),
                        delivery.authSecret(),
                        delivery.payload().getBytes(StandardCharsets.UTF_8),
                        delivery.deadline()
                ));
            } catch (RuntimeException exception) {
                log.error("Could not prepare Customer Push delivery {}", delivery.id(), exception);
                result = new WebPushResult(
                        null,
                        null,
                        "PREPARATION_ERROR",
                        "Customer Push request could not be prepared"
                );
            }
            var completed = completionService.complete(
                    delivery.id(),
                    delivery.claimToken(),
                    delivery.subscriptionId(),
                    clock.instant(),
                    result
            );
            if (!completed) {
                log.warn("Discarded stale Customer Push delivery result for {}", delivery.id());
            }
            meters.counter("kairos.delivery.attempts", "channel", "push",
                    "outcome", !completed ? "stale" : result.isAccepted() ? "accepted"
                    : result.permanentlyInvalidatesSubscription() ? "invalid_subscription"
                    : result.isTransient() ? "transient_failure" : "failed").increment();
        } finally {
            sample.stop(meters.timer("kairos.delivery.attempt.duration", "channel", "push"));
        }
    }
}
