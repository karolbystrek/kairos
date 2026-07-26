package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSigningSecretVersion;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookSigningSecretVersionRepository
        extends JpaRepository<WebhookSigningSecretVersion, UUID> {

    List<WebhookSigningSecretVersion> findAllBySubscriptionIdOrderByIssuedAtDesc(
            UUID subscriptionId
    );

    List<WebhookSigningSecretVersion> findAllBySubscriptionIdInOrderBySubscriptionIdAscIssuedAtDesc(
            Collection<UUID> subscriptionIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<WebhookSigningSecretVersion> findAllForUpdateBySubscriptionIdOrderByIssuedAtDesc(
            UUID subscriptionId
    );

    @Query("""
            SELECT version
            FROM WebhookSigningSecretVersion version
            WHERE version.subscriptionId = :subscriptionId
              AND version.retiredAt IS NULL
              AND (version.validUntil IS NULL OR version.validUntil > :now)
            ORDER BY version.issuedAt DESC
            """)
    List<WebhookSigningSecretVersion> findActiveForDelivery(
            @Param("subscriptionId") UUID subscriptionId,
            @Param("now") Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WebhookSigningSecretVersion> findForUpdateByIdAndSubscriptionId(
            UUID id,
            UUID subscriptionId
    );
}
