package pl.karolbystrek.kairos.api.notification.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushDelivery;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushDeliveryStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPushDeliveryRepository
        extends JpaRepository<CustomerPushDelivery, UUID> {

    boolean existsByOutboxEventIdAndSubscriptionId(UUID outboxEventId, UUID subscriptionId);

    List<CustomerPushDelivery> findAllBySubscriptionIdAndOrderIdAndStatus(
            UUID subscriptionId,
            UUID orderId,
            CustomerPushDeliveryStatus status
    );

    List<CustomerPushDelivery> findAllBySubscriptionIdAndStatus(
            UUID subscriptionId,
            CustomerPushDeliveryStatus status
    );

    List<CustomerPushDelivery> findAllBySubscriptionIdAndOrderIdInAndStatus(
            UUID subscriptionId,
            Collection<UUID> orderIds,
            CustomerPushDeliveryStatus status
    );

    @Query(value = """
            SELECT *
            FROM customer_push_deliveries
            WHERE (
                status = 'PENDING'
                AND next_attempt_at <= :now
            ) OR (
                status = 'PROCESSING'
                AND claim_until <= :now
            )
            ORDER BY next_attempt_at, created_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<CustomerPushDelivery> findAvailableForClaim(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerPushDelivery> findForUpdateByIdAndClaimToken(UUID id, UUID claimToken);

    @Query(value = """
            SELECT *
            FROM customer_push_deliveries
            WHERE (
                status IN ('ACCEPTED', 'SUPERSEDED', 'CANCELED')
                AND completed_at < :successfulCutoff
            ) OR (
                status IN ('EXPIRED', 'DEAD_LETTERED')
                AND completed_at < :failedCutoff
            )
            ORDER BY completed_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<CustomerPushDelivery> findTerminalForCleanup(
            @Param("successfulCutoff") Instant successfulCutoff,
            @Param("failedCutoff") Instant failedCutoff,
            @Param("batchSize") int batchSize
    );
}
