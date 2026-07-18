package pl.karolbystrek.kairos.api.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.order.application.model.StaffOrderView;
import pl.karolbystrek.kairos.api.order.domain.InvalidOrderTransitionException;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.OrderHistoryRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@SpringBootTest
@Transactional
class OrderServiceIntegrationTests {

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderHistoryRepository historyRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private UUID locationId;

	@BeforeEach
	void createTestLocation() {
		UUID tenantId = UUID.randomUUID();
		locationId = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO tenants (id, name) VALUES (?, ?)", tenantId, "Test tenant");
		jdbcTemplate.update(
			"INSERT INTO locations (id, tenant_id, name) VALUES (?, ?, ?)",
			locationId,
			tenantId,
			"Test location"
		);
	}

	@Test
	void createsTransitionsAndTracksAnOrder() {
		var created = orderService.createOrder(locationId, "A-42");

		assertThat(created.status()).isEqualTo(OrderStatus.CREATED);
		assertThat(created.trackingReference()).isNotNull();
		assertThat(orderService.listOrders(locationId)).extracting(StaffOrderView::id)
			.contains(created.id());

		var inPreparation = orderService.updateStatus(created.id(), OrderStatus.IN_PREPARATION);
		var ready = orderService.updateStatus(created.id(), OrderStatus.READY);
		var tracked = orderService.findTrackedOrder(created.trackingReference());

		assertThat(inPreparation.status()).isEqualTo(OrderStatus.IN_PREPARATION);
		assertThat(ready.status()).isEqualTo(OrderStatus.READY);
		assertThat(tracked.label()).isEqualTo("A-42");
		assertThat(tracked.status()).isEqualTo(OrderStatus.READY);
		assertThat(historyRepository.count()).isEqualTo(3);
	}

	@Test
	void rejectsInvalidTransitions() {
		var created = orderService.createOrder(locationId, "B-7");

		assertThatThrownBy(() -> orderService.updateStatus(created.id(), OrderStatus.COMPLETED))
			.isInstanceOf(InvalidOrderTransitionException.class)
			.hasMessageContaining("CREATED to COMPLETED");
	}
}
