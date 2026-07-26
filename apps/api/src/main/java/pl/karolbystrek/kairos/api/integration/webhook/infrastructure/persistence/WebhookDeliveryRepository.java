package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookDelivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    boolean existsByOutboxEventIdAndSubscriptionId(UUID outboxEventId, UUID subscriptionId);

    @Query(value = """
            SELECT *
            FROM webhook_deliveries
            WHERE status = 'PENDING'
               OR (status = 'PROCESSING' AND claim_until <= :now)
            ORDER BY created_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookDelivery> findAvailableForClaim(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WebhookDelivery> findForUpdateByIdAndClaimToken(UUID id, UUID claimToken);
}
