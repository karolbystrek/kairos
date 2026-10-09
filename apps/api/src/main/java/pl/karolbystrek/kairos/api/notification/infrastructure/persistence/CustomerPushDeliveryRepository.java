package pl.karolbystrek.kairos.api.notification.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushDelivery;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushDeliveryStatus;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerPushDelivery> findForUpdateByIdAndClaimToken(UUID id, UUID claimToken);

}
