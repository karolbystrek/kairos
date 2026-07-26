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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookDelivery {

    @Id
    private UUID id;

    @Column(name = "outbox_event_id", nullable = false)
    private UUID outboxEventId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "destination_url", nullable = false, length = 2048)
    private String destinationUrl;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WebhookDeliveryStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "claim_until")
    private Instant claimUntil;

    @Column(name = "attempted_at")
    private Instant attemptedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "response_truncated", nullable = false)
    private boolean responseTruncated;

    @Column(name = "error_type", length = 64)
    private String errorType;

    @Column(name = "error_detail", length = 1024)
    private String errorDetail;

    public static WebhookDelivery create(
            @NonNull UUID outboxEventId,
            @NonNull UUID subscriptionId,
            @NonNull String destinationUrl,
            @NonNull String payload,
            @NonNull Instant createdAt
    ) {
        var delivery = new WebhookDelivery();
        delivery.id = UUID.randomUUID();
        delivery.outboxEventId = outboxEventId;
        delivery.subscriptionId = subscriptionId;
        delivery.destinationUrl = destinationUrl;
        delivery.payload = payload;
        delivery.status = WebhookDeliveryStatus.PENDING;
        delivery.createdAt = createdAt;
        return delivery;
    }

    public void claim(@NonNull UUID token, @NonNull Instant now, @NonNull Duration lease) {
        if (status != WebhookDeliveryStatus.PENDING
                && !(status == WebhookDeliveryStatus.PROCESSING
                && claimUntil != null
                && !claimUntil.isAfter(now))) {
            throw new IllegalStateException("Webhook delivery is not available for claiming");
        }
        status = WebhookDeliveryStatus.PROCESSING;
        claimToken = token;
        claimedAt = now;
        claimUntil = now.plus(lease);
    }

    public void succeed(
            @NonNull UUID token,
            @NonNull Instant attemptedAt,
            @NonNull Instant completedAt,
            int responseStatus,
            String responseBody,
            boolean responseTruncated
    ) {
        requireClaim(token);
        status = WebhookDeliveryStatus.SUCCEEDED;
        this.attemptedAt = attemptedAt;
        this.completedAt = completedAt;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.responseTruncated = responseTruncated;
        clearClaim();
    }

    public void deadLetter(
            @NonNull UUID token,
            @NonNull Instant attemptedAt,
            @NonNull Instant completedAt,
            Integer responseStatus,
            String responseBody,
            boolean responseTruncated,
            @NonNull String errorType,
            String errorDetail
    ) {
        requireClaim(token);
        status = WebhookDeliveryStatus.DEAD_LETTERED;
        this.attemptedAt = attemptedAt;
        this.completedAt = completedAt;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.responseTruncated = responseTruncated;
        this.errorType = errorType;
        this.errorDetail = errorDetail;
        clearClaim();
    }

    private void requireClaim(UUID token) {
        if (status != WebhookDeliveryStatus.PROCESSING || !token.equals(claimToken)) {
            throw new IllegalStateException("Webhook delivery claim no longer belongs to this worker");
        }
    }

    private void clearClaim() {
        claimToken = null;
        claimUntil = null;
    }
}
