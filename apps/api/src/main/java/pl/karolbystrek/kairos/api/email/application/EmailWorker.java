package pl.karolbystrek.kairos.api.email.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.karolbystrek.kairos.api.worker.CoalescingWorker;
import pl.karolbystrek.kairos.api.worker.WorkerCycles;

@Component
public class EmailWorker {
    private final CoalescingWorker worker;

    EmailWorker(EmailDeliveryWorker delivery, WorkerCycles cycles) {
        worker = new CoalescingWorker(() -> cycles.run(
                "email-delivery", "Could not deliver transactional email", delivery::deliverAvailable));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void committed(EmailDeliveryService.Queued event) {
        worker.wake();
    }

    @Scheduled(fixedDelayString = "${kairos.email.poll-delay:30s}",
            initialDelayString = "${kairos.email.initial-delay:1s}")
    void recover() {
        worker.wake();
    }
}
