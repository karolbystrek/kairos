package pl.karolbystrek.kairos.api.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "customer_push_deliveries",
        uniqueConstraints = @UniqueConstraint(
                name = "customer_push_deliveries_event_subscription_key",
                columnNames = {"outbox_event_id", "subscription_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerPushDelivery {

    @Id
    private UUID id;

    @Column(name = "outbox_event_id", nullable = false)
    private UUID outboxEventId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "endpoint_fingerprint", nullable = false, length = 64)
    private String endpointFingerprint;

    @Column(name = "push_service_origin", nullable = false, length = 255)
    private String pushServiceOrigin;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CustomerPushDeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "claim_until")
    private Instant claimUntil;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "outcome", length = 64)
    private String outcome;

    @Column(name = "diagnostic", length = 1024)
    private String diagnostic;

    public static CustomerPushDelivery create(
            @NonNull UUID outboxEventId,
            @NonNull UUID subscriptionId,
            @NonNull UUID orderId,
            @NonNull String endpointFingerprint,
            @NonNull String pushServiceOrigin,
            @NonNull String payload,
            @NonNull Instant deadlineAt,
            @NonNull Instant now
    ) {
        var delivery = new CustomerPushDelivery();
        delivery.id = UUID.randomUUID();
        delivery.outboxEventId = outboxEventId;
        delivery.subscriptionId = subscriptionId;
        delivery.orderId = orderId;
        delivery.endpointFingerprint = endpointFingerprint;
        delivery.pushServiceOrigin = pushServiceOrigin;
        delivery.payload = payload;
        delivery.status = CustomerPushDeliveryStatus.PENDING;
        delivery.nextAttemptAt = now;
        delivery.deadlineAt = deadlineAt;
        delivery.createdAt = now;
        return delivery;
    }

    public void claim(@NonNull UUID token, @NonNull Instant now, @NonNull Duration lease) {
        if (status != CustomerPushDeliveryStatus.PENDING
                && !(status == CustomerPushDeliveryStatus.PROCESSING
                && claimUntil != null
                && !claimUntil.isAfter(now))) {
            throw new IllegalStateException("Customer Push delivery is not available for claiming");
        }
        status = CustomerPushDeliveryStatus.PROCESSING;
        claimToken = token;
        claimedAt = now;
        claimUntil = now.plus(lease);
        attemptCount = Math.incrementExact(attemptCount);
    }

    public void accept(@NonNull UUID token, @NonNull Instant now, int responseStatus) {
        finish(token, CustomerPushDeliveryStatus.ACCEPTED, now, responseStatus, "ACCEPTED", null);
    }

    public void retry(
            @NonNull UUID token,
            @NonNull Instant nextAttemptAt,
            Integer responseStatus,
            @NonNull String outcome,
            String diagnostic
    ) {
        requireClaim(token);
        status = CustomerPushDeliveryStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        this.responseStatus = responseStatus;
        this.outcome = outcome;
        this.diagnostic = diagnostic;
        clearClaim();
    }

    public void deadLetter(
            @NonNull UUID token,
            @NonNull Instant now,
            Integer responseStatus,
            @NonNull String outcome,
            String diagnostic
    ) {
        finish(
                token,
                CustomerPushDeliveryStatus.DEAD_LETTERED,
                now,
                responseStatus,
                outcome,
                diagnostic
        );
    }

    public void expire(@NonNull UUID token, @NonNull Instant now, String diagnostic) {
        finish(token, CustomerPushDeliveryStatus.EXPIRED, now, null, "EXPIRED", diagnostic);
    }

    public void supersede(@NonNull Instant now) {
        finishUnclaimed(CustomerPushDeliveryStatus.SUPERSEDED, now, "SUPERSEDED");
    }

    public void cancel(@NonNull Instant now) {
        finishUnclaimed(CustomerPushDeliveryStatus.CANCELED, now, "SUBSCRIPTION_REMOVED");
    }

    private void finish(
            UUID token,
            CustomerPushDeliveryStatus target,
            Instant now,
            Integer responseStatus,
            String outcome,
            String diagnostic
    ) {
        requireClaim(token);
        status = target;
        completedAt = now;
        this.responseStatus = responseStatus;
        this.outcome = outcome;
        this.diagnostic = diagnostic;
        payload = null;
        clearClaim();
    }

    private void finishUnclaimed(
            CustomerPushDeliveryStatus target,
            Instant now,
            String outcome
    ) {
        if (status != CustomerPushDeliveryStatus.PENDING) {
            return;
        }
        status = target;
        completedAt = now;
        this.outcome = outcome;
        payload = null;
    }

    private void requireClaim(UUID token) {
        if (status != CustomerPushDeliveryStatus.PROCESSING || !token.equals(claimToken)) {
            throw new IllegalStateException("Customer Push delivery claim no longer belongs to this worker");
        }
    }

    private void clearClaim() {
        claimToken = null;
        claimUntil = null;
    }
}
