package pl.karolbystrek.kairos.api.notification.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushDeliveryClaimService;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushDeliveryProcessor;

import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
class CustomerPushDeliveryScheduler {

    private final CustomerPushDeliveryClaimService claimService;
    private final CustomerPushDeliveryProcessor deliveryProcessor;

    @Scheduled(
            fixedDelayString = "${kairos.customer-notifications.worker.delivery-poll-delay:500ms}",
            initialDelayString = "${kairos.customer-notifications.worker.initial-delay:1s}"
    )
    void deliver() {
        try {
            var claimed = claimService.claimAvailable();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                claimed.forEach(delivery -> executor.submit(() -> {
                    try {
                        deliveryProcessor.process(delivery);
                    } catch (RuntimeException exception) {
                        log.error(
                                "Customer Push delivery {} failed unexpectedly",
                                delivery.id(),
                                exception
                        );
                    }
                }));
            }
        } catch (RuntimeException exception) {
            log.error("Customer Push delivery cycle failed", exception);
        }
    }
}
