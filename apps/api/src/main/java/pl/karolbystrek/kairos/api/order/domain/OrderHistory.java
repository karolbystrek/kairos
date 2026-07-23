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
import lombok.NonNull;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "initiator_type", length = 32)
    private InitiatorType initiatorType;

    @Column(name = "initiator_id")
    private UUID initiatorId;

    public static OrderHistory record(
        @NonNull CustomerOrder order,
        @NonNull OrderStatus status,
        @NonNull Instant createdAt
    ) {
        var history = new OrderHistory();
        history.order = order;
        history.status = status;
        history.createdAt = createdAt;
        return history;
    }

    public static OrderHistory recordByUser(
        @NonNull CustomerOrder order,
        @NonNull OrderStatus status,
        @NonNull Instant createdAt,
        @NonNull UUID accountId
    ) {
        var history = record(order, status, createdAt);
        history.initiatorType = InitiatorType.USER;
        history.initiatorId = accountId;
        return history;
    }
}
