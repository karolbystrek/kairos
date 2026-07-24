package pl.karolbystrek.kairos.api.order.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    List<CustomerOrder> findAllByLocationIdAndStatusInOrderByCreatedAtDesc(
            UUID locationId,
            Collection<OrderStatus> statuses
    );

    List<CustomerOrder> findAllByLocationTenantIdAndStatusInOrderByCreatedAtDesc(
            UUID tenantId,
            Collection<OrderStatus> statuses
    );

    Optional<CustomerOrder> findByTrackingReference(UUID trackingReference);

    long countByLocationIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID locationId,
            Instant startInclusive,
            Instant endExclusive
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerOrder> findForUpdateById(UUID orderId);
}
