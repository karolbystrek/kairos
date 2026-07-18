package pl.karolbystrek.kairos.api.order.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

	List<CustomerOrder> findAllByLocationIdOrderByCreatedAtDesc(UUID locationId);

	Optional<CustomerOrder> findByTrackingReference(UUID trackingReference);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select orders from CustomerOrder orders where orders.id = :orderId")
	Optional<CustomerOrder> findByIdForUpdate(@Param("orderId") UUID orderId);
}
