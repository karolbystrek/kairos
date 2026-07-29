package pl.karolbystrek.kairos.api.order.domain;

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
@Table(name = "order_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderOutboxEvent {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "tracking_reference", nullable = false)
    private UUID trackingReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private OrderEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "webhook_payload", nullable = false, columnDefinition = "TEXT")
    private String webhookPayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "webhook_fanout_completed_at")
    private Instant webhookFanoutCompletedAt;

    @Column(name = "push_fanout_completed_at")
    private Instant pushFanoutCompletedAt;

    public static OrderOutboxEvent create(
            @NonNull UUID id,
            @NonNull UUID orderId,
            @NonNull UUID tenantId,
            @NonNull UUID locationId,
            @NonNull UUID trackingReference,
            @NonNull OrderEventType eventType,
            @NonNull OrderStatus status,
            @NonNull Instant occurredAt,
            @NonNull String webhookPayload,
            @NonNull Instant createdAt
    ) {
        var event = new OrderOutboxEvent();
        event.id = id;
        event.orderId = orderId;
        event.tenantId = tenantId;
        event.locationId = locationId;
        event.trackingReference = trackingReference;
        event.eventType = eventType;
        event.status = status;
        event.occurredAt = occurredAt;
        event.webhookPayload = webhookPayload;
        event.createdAt = createdAt;
        return event;
    }

    public void completeWebhookFanout(@NonNull Instant now) {
        if (webhookFanoutCompletedAt == null) {
            webhookFanoutCompletedAt = now;
        }
    }

    public void completePushFanout(@NonNull Instant now) {
        if (pushFanoutCompletedAt == null) {
            pushFanoutCompletedAt = now;
        }
    }
}
