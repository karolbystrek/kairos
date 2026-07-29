package pl.karolbystrek.kairos.api.notification.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushOutboxFanoutService;

@Component
@RequiredArgsConstructor
@Slf4j
class CustomerPushOutboxFanoutScheduler {

    private final CustomerPushOutboxFanoutService fanoutService;

    @Scheduled(
            fixedDelayString = "${kairos.customer-notifications.worker.fanout-poll-delay:500ms}",
            initialDelayString = "${kairos.customer-notifications.worker.initial-delay:1s}"
    )
    void fanOut() {
        try {
            fanoutService.fanOutAvailable();
        } catch (RuntimeException exception) {
            log.error("Customer Push outbox fan-out cycle failed", exception);
        }
    }
}
