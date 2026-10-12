package pl.karolbystrek.kairos.api.analytics.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.analytics.application.OrderAnalyticsService;

@Component
@RequiredArgsConstructor
@Slf4j
class OrderAnalyticsScheduler {
    private final OrderAnalyticsService analytics;

    @Scheduled(fixedDelayString = "${kairos.analytics.poll-delay:10s}",
            initialDelayString = "${kairos.analytics.initial-delay:1s}")
    void process() {
        try {
            analytics.processAvailable();
        } catch (RuntimeException exception) {
            log.error("Order analytics cycle failed; uncommitted measurements will be retried", exception);
        }
    }
}
