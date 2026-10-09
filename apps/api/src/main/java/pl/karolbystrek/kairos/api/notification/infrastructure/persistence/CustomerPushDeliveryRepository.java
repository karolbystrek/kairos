package pl.karolbystrek.kairos.api.notification.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import pl.karolbystrek.kairos.api.notification.domain.CustomerPushDelivery;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushDeliveryStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerPushDelivery> findForUpdateByIdAndClaimToken(UUID id, UUID claimToken);

    @Query(value = "SELECT public.cancel_push_deliveries(:subscriptionId, CAST(:orderIds AS uuid[]), :now)", nativeQuery = true)
    int cancelPendingQuery(UUID subscriptionId, String orderIds, Instant now);

    default void cancelPending(UUID subscriptionId, Collection<UUID> orderIds, Instant now) {
        cancelPendingQuery(subscriptionId, orderIds == null ? null : orderIds.stream()
                .map(UUID::toString).collect(Collectors.joining(",", "{", "}")), now);
    }

}
