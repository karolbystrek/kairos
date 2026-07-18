package pl.karolbystrek.kairos.api.order.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.karolbystrek.kairos.api.order.domain.OrderHistory;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, Long> {
}
