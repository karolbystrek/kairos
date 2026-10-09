package pl.karolbystrek.kairos.api.order.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.karolbystrek.kairos.api.order.domain.OrderOutboxEvent;
import java.util.UUID;

public interface OrderOutboxEventRepository extends JpaRepository<OrderOutboxEvent, UUID> {
}
