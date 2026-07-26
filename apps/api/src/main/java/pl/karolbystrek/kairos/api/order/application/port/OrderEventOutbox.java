package pl.karolbystrek.kairos.api.order.application.port;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;

import java.time.Instant;

public interface OrderEventOutbox {

    void recordCreated(@NonNull CustomerOrder order, @NonNull Instant occurredAt);

    void recordStatusChanged(@NonNull CustomerOrder order, @NonNull Instant occurredAt);
}
