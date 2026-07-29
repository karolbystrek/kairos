package pl.karolbystrek.kairos.api.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "customer_push_enrollments",
        uniqueConstraints = @UniqueConstraint(
                name = "customer_push_enrollments_subscription_order_key",
                columnNames = {"subscription_id", "order_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerPushEnrollment {

    @Id
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static CustomerPushEnrollment create(
            @NonNull UUID subscriptionId,
            @NonNull UUID orderId,
            @NonNull Instant now
    ) {
        var enrollment = new CustomerPushEnrollment();
        enrollment.id = UUID.randomUUID();
        enrollment.subscriptionId = subscriptionId;
        enrollment.orderId = orderId;
        enrollment.createdAt = now;
        return enrollment;
    }
}
