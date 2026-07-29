package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {

    List<WebhookSubscription> findAllByIntegrationIdAndArchivedAtIsNullOrderByCreatedAt(
            UUID integrationId
    );

    Optional<WebhookSubscription> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIntegrationIdAndNormalizedName(UUID integrationId, String normalizedName);

    boolean existsByIntegrationIdAndNormalizedNameAndIdNot(
            UUID integrationId,
            String normalizedName,
            UUID excludedId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WebhookSubscription> findForUpdateByIdAndTenantId(UUID id, UUID tenantId);

    @Query(value = """
            SELECT subscription.*
            FROM webhook_subscriptions subscription
            JOIN external_integrations integration
              ON integration.id = subscription.integration_id
             AND integration.tenant_id = subscription.tenant_id
            JOIN webhook_subscription_location_access location_access
              ON location_access.subscription_id = subscription.id
             AND location_access.tenant_id = subscription.tenant_id
            JOIN webhook_subscription_event_types event_selection
              ON event_selection.subscription_id = subscription.id
            WHERE subscription.tenant_id = :tenantId
              AND subscription.status = 'ENABLED'
              AND integration.status = 'ENABLED'
              AND subscription.last_enabled_at <= :occurredAt
              AND integration.last_enabled_at <= :occurredAt
              AND location_access.location_id = :locationId
              AND event_selection.event_type = :eventType
            ORDER BY subscription.created_at, subscription.id
            FOR UPDATE
            """, nativeQuery = true)
    List<WebhookSubscription> findMatchingForFanout(
            @Param("tenantId") UUID tenantId,
            @Param("locationId") UUID locationId,
            @Param("eventType") String eventType,
            @Param("occurredAt") java.time.Instant occurredAt
    );
}
