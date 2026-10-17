package pl.karolbystrek.kairos.api.order.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository
        extends JpaRepository<CustomerOrder, UUID>, JpaSpecificationExecutor<CustomerOrder> {

    List<CustomerOrder> findAllByLocationIdAndStatusInOrderByCreatedAtDesc(
            UUID locationId,
            Collection<OrderStatus> statuses
    );

    List<CustomerOrder> findAllByLocationTenantIdAndStatusInOrderByCreatedAtDesc(
            UUID tenantId,
            Collection<OrderStatus> statuses
    );

    Optional<CustomerOrder> findByTrackingReference(UUID trackingReference);

    List<CustomerOrder> findAllByTrackingReferenceIn(Collection<UUID> trackingReferences);

    Optional<CustomerOrder> findByIdAndLocationIdIn(UUID orderId, Collection<UUID> locationIds);

    Optional<CustomerOrder> findByExternalIntegrationIdAndLocationIdAndExternalIdempotencyKey(
            UUID integrationId,
            UUID locationId,
            String idempotencyKey
    );

    // The counter row lock serializes concurrent creation for one location and business day.
    @Query(value = """
            INSERT INTO order_number_counters (location_id, business_date, last_number)
            VALUES (:locationId, :businessDate, 1)
            ON CONFLICT (location_id, business_date)
            DO UPDATE SET last_number = order_number_counters.last_number + 1
            RETURNING last_number
            """, nativeQuery = true)
    long allocateNumber(UUID locationId, LocalDate businessDate);

    boolean existsByLocationIdAndStatusIn(
        UUID locationId,
        Collection<OrderStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerOrder> findForUpdateById(UUID orderId);

    // Scalar read: no managed order is cached, so the later FOR UPDATE load sees fresh state.
    @Query("select o.location.id from CustomerOrder o where o.id = :orderId")
    Optional<UUID> findLocationIdById(UUID orderId);

    // Locks all referenced orders in one query, in a stable order so concurrent enrollments cannot deadlock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<CustomerOrder> findAllForUpdateByTrackingReferenceInOrderByTrackingReference(
            Collection<UUID> trackingReferences
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerOrder> findForUpdateByIdAndLocationIdIn(
            UUID orderId,
            Collection<UUID> locationIds
    );
}
