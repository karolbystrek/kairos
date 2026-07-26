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
@Table(name = "webhook_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookOutboxEvent {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private WebhookEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "fanout_completed_at")
    private Instant fanoutCompletedAt;

    public static WebhookOutboxEvent create(
            @NonNull UUID id,
            @NonNull UUID orderId,
            @NonNull UUID tenantId,
            @NonNull UUID locationId,
            @NonNull WebhookEventType eventType,
            @NonNull Instant occurredAt,
            @NonNull String payload,
            @NonNull Instant createdAt
    ) {
        var event = new WebhookOutboxEvent();
        event.id = id;
        event.orderId = orderId;
        event.tenantId = tenantId;
        event.locationId = locationId;
        event.eventType = eventType;
        event.occurredAt = occurredAt;
        event.payload = payload;
        event.createdAt = createdAt;
        return event;
    }

    public void completeFanout(@NonNull Instant now) {
        if (fanoutCompletedAt == null) {
            fanoutCompletedAt = now;
        }
    }
}
