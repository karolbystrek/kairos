package pl.karolbystrek.kairos.api.notification.infrastructure.worker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.karolbystrek.kairos.api.order.application.model.OrderStatusChangedEvent;
import pl.karolbystrek.kairos.api.worker.CoalescingWorker;

@Component
public class CustomerPushWorker {

    private final CoalescingWorker worker;

    CustomerPushWorker(CustomerPushOutboxFanoutScheduler fanout, CustomerPushDeliveryScheduler delivery) {
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
            fixedDelayString = "${kairos.customer-notifications.worker.recovery-poll-delay:5s}",
            initialDelayString = "${kairos.customer-notifications.worker.initial-delay:1s}"
    )
    void recover() {
        worker.wake();
    }
}
