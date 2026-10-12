package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.worker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.worker.CoalescingWorker;

@Component
public class WebhookWorker {

    private final CoalescingWorker worker;

    WebhookWorker(WebhookOutboxFanoutScheduler fanout, WebhookDeliveryScheduler delivery) {
        worker = new CoalescingWorker(() -> {
            var fannedOut = fanout.fanOut();
            var delivered = delivery.deliver();
            return fannedOut || delivered;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void committed(OrderStatusChangedEvent event) {
        worker.wake();
    }

    @Scheduled(
            fixedDelayString = "${kairos.webhooks.worker.recovery-poll-delay:5s}",
            initialDelayString = "${kairos.webhooks.worker.initial-delay:1s}"
    )
    void recover() {
        worker.wake();
    }
}
