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
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
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

	@Column(nullable = false, length = 80)
	private String label;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private OrderStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public static CustomerOrder create(Location location, String label, Instant now) {
		CustomerOrder order = new CustomerOrder();
		order.location = Objects.requireNonNull(location);
		order.trackingReference = UUID.randomUUID();
		order.label = Objects.requireNonNull(label);
		order.status = OrderStatus.CREATED;
		order.createdAt = Objects.requireNonNull(now);
		order.updatedAt = now;
		return order;
	}

	public void transitionTo(OrderStatus target, Instant now) {
		if (!status.canTransitionTo(target)) {
			throw new InvalidOrderTransitionException(status, target);
		}

		status = target;
		updatedAt = now;
	}
}
