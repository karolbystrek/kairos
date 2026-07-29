package pl.karolbystrek.kairos.api.order.application.port;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;

import java.time.Instant;
import java.util.UUID;

public interface OrderEventOutbox {

    UUID recordCreated(@NonNull CustomerOrder order, @NonNull Instant occurredAt);

    UUID recordStatusChanged(@NonNull CustomerOrder order, @NonNull Instant occurredAt);
}
