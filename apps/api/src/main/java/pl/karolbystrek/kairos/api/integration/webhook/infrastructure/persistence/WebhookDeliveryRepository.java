package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookDelivery;

import java.util.Optional;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    boolean existsByOutboxEventIdAndSubscriptionId(UUID outboxEventId, UUID subscriptionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WebhookDelivery> findForUpdateByIdAndClaimToken(UUID id, UUID claimToken);
}
