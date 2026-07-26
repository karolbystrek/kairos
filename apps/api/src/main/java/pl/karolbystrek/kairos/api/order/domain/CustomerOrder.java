package pl.karolbystrek.kairos.api.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;
import pl.karolbystrek.kairos.api.location.domain.Location;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "tracking_reference", nullable = false, unique = true)
    private UUID trackingReference;

    @Column(nullable = false, length = 32)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "external_integration_id")
    private UUID externalIntegrationId;

    @Column(name = "external_idempotency_key", length = 255)
    private String externalIdempotencyKey;

    @Column(name = "external_request_fingerprint", length = 64)
    private String externalRequestFingerprint;

    public static CustomerOrder create(
            @NonNull Location location,
            @NonNull String label,
            @NonNull Instant now
    ) {
        var order = new CustomerOrder();
        order.location = location;
        order.trackingReference = UUID.randomUUID();
        order.label = label;
        order.status = OrderStatus.IN_PREPARATION;
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    public static CustomerOrder createByIntegration(
            @NonNull Location location,
            @NonNull String label,
            @NonNull Instant now,
            @NonNull UUID integrationId,
            @NonNull String idempotencyKey,
            @NonNull String requestFingerprint
    ) {
        var order = create(location, label, now);
        order.externalIntegrationId = integrationId;
        order.externalIdempotencyKey = idempotencyKey;
        order.externalRequestFingerprint = requestFingerprint;
        return order;
    }

    public boolean transitionTo(@NonNull OrderStatus target, @NonNull Instant now) {
        if (status == target) {
            return false;
        }
        if (!status.canTransitionTo(target)) {
            throw new InvalidOrderTransitionException(status, target);
        }

        status = target;
        updatedAt = now;
        return true;
    }
}
