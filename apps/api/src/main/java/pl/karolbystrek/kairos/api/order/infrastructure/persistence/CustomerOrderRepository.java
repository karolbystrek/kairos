package pl.karolbystrek.kairos.api.order.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    List<CustomerOrder> findAllByLocationIdOrderByCreatedAtDesc(UUID locationId);

    List<CustomerOrder> findAllByLocationTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<CustomerOrder> findByTrackingReference(UUID trackingReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerOrder> findForUpdateById(UUID orderId);
}
