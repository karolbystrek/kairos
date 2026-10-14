package pl.karolbystrek.kairos.api.notification.infrastructure.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushOutboxFanoutService;
import pl.karolbystrek.kairos.api.worker.WorkerCycles;

@Component
@RequiredArgsConstructor
class CustomerPushOutboxFanoutScheduler {

    private final WorkerCycles cycles;
    private final CustomerPushOutboxFanoutService fanoutService;

    boolean fanOut() {
        return cycles.run("push_fanout", "Customer Push outbox fan-out cycle failed",
                () -> fanoutService.fanOutAvailable() > 0);
    }
}
