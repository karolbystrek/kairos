package pl.karolbystrek.kairos.api.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.location.application.LocationService;
import pl.karolbystrek.kairos.api.order.application.model.StaffOrderView;
import pl.karolbystrek.kairos.api.order.domain.InvalidOrderTransitionException;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.OrderHistoryRepository;

import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@SpringBootTest
@Transactional
class OrderServiceIntegrationTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private LocationService locationService;

    @Autowired
    private OrderHistoryRepository historyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID locationId;
    private StaffPrincipal principal;

    @BeforeEach
    void createTestLocation() {
        var tenantId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        jdbcTemplate.update(
            "INSERT INTO locations (id, tenant_id) VALUES (?, ?)",
            locationId,
            tenantId
        );
        var now = Instant.now();
        jdbcTemplate.update(
            """
            INSERT INTO accounts (
                id, tenant_id, username, tenant_role, status, created_at, updated_at
            ) VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', ?, ?)
            """,
            accountId,
            tenantId,
            "admin-" + accountId,
            now,
            now
        );
        principal = new StaffPrincipal(accountId, tenantId, TenantRole.ADMIN);
    }

    @Test
    void createsTransitionsAndTracksAnOrder() {
        var created = orderService.createOrder(principal, locationId);

        assertThat(created.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(created.trackingReference()).isNotNull();
        assertThat(locationService.listAccessible(principal)).extracting(location -> location.id())
            .containsExactly(locationId);
        assertThat(orderService.listOrders(principal, locationId)).extracting(StaffOrderView::id)
            .contains(created.id());

        var inPreparation = orderService.updateStatus(principal, created.id(), OrderStatus.IN_PREPARATION);
        var ready = orderService.updateStatus(principal, created.id(), OrderStatus.READY);
        var tracked = orderService.findTrackedOrder(created.trackingReference());

        assertThat(inPreparation.status()).isEqualTo(OrderStatus.IN_PREPARATION);
        assertThat(ready.status()).isEqualTo(OrderStatus.READY);
        assertThat(tracked.status()).isEqualTo(OrderStatus.READY);
        assertThat(historyRepository.count()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList(
            "SELECT initiator_type FROM order_history WHERE order_id = ? ORDER BY id",
            String.class,
            created.id()
        )).containsOnly("USER");
        assertThat(jdbcTemplate.queryForList(
            "SELECT initiator_id FROM order_history WHERE order_id = ? ORDER BY id",
            UUID.class,
            created.id()
        )).containsOnly(principal.accountId());
    }

    @Test
    void rejectsInvalidTransitions() {
        var created = orderService.createOrder(principal, locationId);

        assertThatThrownBy(() -> orderService.updateStatus(principal, created.id(), OrderStatus.COMPLETED))
            .isInstanceOf(InvalidOrderTransitionException.class)
            .hasMessageContaining("CREATED to COMPLETED");
    }
}
