package pl.karolbystrek.kairos.api.integration.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookSubscription {

    @Id
    private UUID id;

    @Column(name = "integration_id", nullable = false)
    private UUID integrationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 128)
    private String normalizedName;

    @Column(name = "destination_url", nullable = false, length = 2048)
    private String destinationUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WebhookSubscriptionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_enabled_at")
    private Instant lastEnabledAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public static WebhookSubscription create(
            @NonNull UUID integrationId,
            @NonNull UUID tenantId,
            @NonNull String name,
            @NonNull String normalizedName,
            @NonNull String destinationUrl,
            @NonNull Instant now
    ) {
        var subscription = new WebhookSubscription();
        subscription.id = UUID.randomUUID();
        subscription.integrationId = integrationId;
        subscription.tenantId = tenantId;
        subscription.name = name;
        subscription.normalizedName = normalizedName;
        subscription.destinationUrl = destinationUrl;
        subscription.status = WebhookSubscriptionStatus.DISABLED;
        subscription.createdAt = now;
        subscription.updatedAt = now;
        return subscription;
    }

    public void reconfigure(
            @NonNull String name,
            @NonNull String normalizedName,
            @NonNull String destinationUrl,
            @NonNull Instant now
    ) {
        requireNotArchived();
        this.name = name;
        this.normalizedName = normalizedName;
        this.destinationUrl = destinationUrl;
        updatedAt = now;
    }

    public void enable(@NonNull Instant now) {
        requireNotArchived();
        if (status == WebhookSubscriptionStatus.ENABLED) {
            return;
        }
        status = WebhookSubscriptionStatus.ENABLED;
        updatedAt = now;
        lastEnabledAt = now;
    }

    public void disable(@NonNull Instant now) {
        requireNotArchived();
        if (status == WebhookSubscriptionStatus.DISABLED) {
            return;
        }
        status = WebhookSubscriptionStatus.DISABLED;
        updatedAt = now;
    }

    public void archive(@NonNull Instant now) {
        if (status == WebhookSubscriptionStatus.ARCHIVED) {
            return;
        }
        status = WebhookSubscriptionStatus.ARCHIVED;
        archivedAt = now;
        updatedAt = now;
    }

    private void requireNotArchived() {
        if (status == WebhookSubscriptionStatus.ARCHIVED) {
            throw new IllegalStateException("An archived webhook subscription cannot be changed");
        }
    }
}
