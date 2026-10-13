package pl.karolbystrek.kairos.api.order.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;
import pl.karolbystrek.kairos.api.location.domain.Location;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOrder {

    public static final int MAXIMUM_LABEL_LENGTH = 80;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "tracking_reference", nullable = false, unique = true)
    private UUID trackingReference;

    @Column(nullable = false, updatable = false)
    private long number;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(length = MAXIMUM_LABEL_LENGTH)
    private String label;

    // Queue snapshots map many orders at once; batching avoids one items query per order.
    @BatchSize(size = 100)
    @ElementCollection
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "position")
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "review_due_at")
    private Instant reviewDueAt;

    @Column(name = "review_configuration_id")
    private UUID reviewConfigurationId;

    @Column(name = "external_integration_id")
    private UUID externalIntegrationId;

    @Column(name = "external_idempotency_key", length = 255)
    private String externalIdempotencyKey;

    @Column(name = "external_request_fingerprint", length = 64)
    private String externalRequestFingerprint;

    public static CustomerOrder create(
            @NonNull Location location,
            long number,
            @NonNull LocalDate businessDate,
            String label,
            @NonNull List<OrderItem> items,
            @NonNull Instant now
    ) {
        var order = new CustomerOrder();
        order.location = location;
        order.trackingReference = UUID.randomUUID();
        order.number = number;
        order.businessDate = businessDate;
        order.label = label;
        order.items = new ArrayList<>(items);
        order.status = OrderStatus.IN_PREPARATION;
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    public static CustomerOrder createByIntegration(
            @NonNull Location location,
            long number,
            @NonNull LocalDate businessDate,
            String label,
            @NonNull List<OrderItem> items,
            @NonNull Instant now,
            @NonNull UUID integrationId,
            @NonNull String idempotencyKey,
            @NonNull String requestFingerprint
    ) {
        var order = create(location, number, businessDate, label, items, now);
        order.externalIntegrationId = integrationId;
        order.externalIdempotencyKey = idempotencyKey;
        order.externalRequestFingerprint = requestFingerprint;
        return order;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public void scheduleReview(@NonNull Instant dueAt) {
        if (status == OrderStatus.COMPLETED && location.isEnabled() && location.getGoogleReviewUrl() != null) {
            reviewDueAt = dueAt;
            reviewConfigurationId = location.getReviewConfigurationId();
        }
    }

    public boolean isReviewEligible() {
        return status == OrderStatus.COMPLETED && reviewDueAt != null && reviewConfigurationId != null
                && location.isEnabled() && location.getGoogleReviewUrl() != null
                && reviewConfigurationId.equals(location.getReviewConfigurationId());
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
