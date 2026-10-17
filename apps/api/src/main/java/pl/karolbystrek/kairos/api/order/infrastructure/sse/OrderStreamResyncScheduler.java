package pl.karolbystrek.kairos.api.order.infrastructure.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.order.application.OrderService;

// A lost Redis message must not leave a screen stale: every open stream periodically re-reads its state from PostgreSQL.
@Component
@RequiredArgsConstructor
class OrderStreamResyncScheduler {

    private final OrderSseEmitterRegistry customerStreams;
    private final StaffOrderSseEmitterRegistry staffStreams;
    private final OrderService orders;

    @Scheduled(fixedDelayString = "${kairos.live-updates.resync-interval:60s}")
    void resync() {
        customerStreams.refresh(orders);
        staffStreams.revalidate();
    }
}
