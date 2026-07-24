package pl.karolbystrek.kairos.api.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.location.application.LocationService;
import pl.karolbystrek.kairos.api.location.application.model.LocationView;
import pl.karolbystrek.kairos.api.order.application.model.StaffOrderView;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class OrderAuthorizationIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private LocationService locationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private UUID locationId;
    private UUID otherLocationId;
    private StaffPrincipal admin;
    private StaffPrincipal manager;
    private StaffPrincipal operator;
    private StaffPrincipal suspendedManager;
    private StaffPrincipal otherAdmin;

    @BeforeEach
    void createAuthorizationFixtures() {
        tenantId = UUID.randomUUID();
        var otherTenantId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        otherLocationId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", otherTenantId);
        jdbcTemplate.update(
            "INSERT INTO locations (id, tenant_id) VALUES (?, ?)",
            locationId,
            tenantId
        );
        jdbcTemplate.update(
            "INSERT INTO locations (id, tenant_id) VALUES (?, ?)",
            otherLocationId,
            otherTenantId
        );

        admin = insertAccount(tenantId, TenantRole.ADMIN, "ACTIVE", null, null, null);
        manager = insertAccount(tenantId, TenantRole.MEMBER, "ACTIVE", locationId, "MANAGER", "ACTIVE");
        operator = insertAccount(tenantId, TenantRole.MEMBER, "ACTIVE", locationId, "OPERATOR", "ACTIVE");
        suspendedManager = insertAccount(
            tenantId,
            TenantRole.MEMBER,
            "ACTIVE",
            locationId,
            "MANAGER",
            "SUSPENDED"
        );
        otherAdmin = insertAccount(otherTenantId, TenantRole.ADMIN, "ACTIVE", null, null, null);
    }

    @Test
    void scopesLocationsAndOrdersToTheCurrentTenantAndAssignment() {
        var ownOrder = orderService.createOrder(admin, locationId, null);
        var otherOrder = orderService.createOrder(otherAdmin, otherLocationId, null);

        assertThat(locationService.listAccessible(admin)).extracting(LocationView::id)
            .containsExactly(locationId);
        assertThat(locationService.listAccessible(manager)).extracting(LocationView::id)
            .containsExactly(locationId);
        assertThat(orderService.listTenantOrders(admin)).extracting(StaffOrderView::id)
            .containsExactly(ownOrder.id())
            .doesNotContain(otherOrder.id());

        assertThatThrownBy(() -> orderService.listOrders(manager, otherLocationId))
            .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> orderService.createOrder(operator, otherLocationId, null))
            .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> orderService.listTenantOrders(manager))
            .isInstanceOf(StaffAccessDeniedException.class);
    }

    @Test
    void authorizesTheStoredOrderLocationAndRecordsTheAuthenticatedInitiator() {
        var order = orderService.createOrder(admin, locationId, null);

        var updated = orderService.updateStatus(operator, order.id(), OrderStatus.READY);
        assertThat(updated.status()).isEqualTo(OrderStatus.READY);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT initiator_id FROM order_history WHERE order_id = ? ORDER BY id DESC LIMIT 1",
            UUID.class,
            order.id()
        )).isEqualTo(operator.accountId());

        var otherOrder = orderService.createOrder(otherAdmin, otherLocationId, null);
        assertThatThrownBy(() -> orderService.updateStatus(manager, otherOrder.id(), OrderStatus.READY))
            .isInstanceOf(StaffAccessDeniedException.class);
    }

    @Test
    void rejectsDisabledAccountsAndSuspendedAssignments() {
        assertThatThrownBy(() -> locationService.listAccessible(suspendedManager))
            .isInstanceOf(StaffAccessDeniedException.class);

        jdbcTemplate.update("UPDATE accounts SET status = 'DISABLED' WHERE id = ?", operator.accountId());
        assertThatThrownBy(() -> orderService.createOrder(operator, locationId, null))
            .isInstanceOf(StaffAccessDeniedException.class);
    }

    private StaffPrincipal insertAccount(
        UUID accountTenantId,
        TenantRole tenantRole,
        String accountStatus,
        UUID assignedLocationId,
        String assignmentRole,
        String assignmentStatus
    ) {
        var accountId = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update(
            """
            INSERT INTO accounts (
                id, tenant_id, username, tenant_role, status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            accountId,
            accountTenantId,
            "account-" + accountId,
            tenantRole.name(),
            accountStatus,
            now,
            now
        );
        if (assignedLocationId != null) {
            jdbcTemplate.update(
                """
                INSERT INTO location_assignments (
                    account_id, location_id, tenant_id, role, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                accountId,
                assignedLocationId,
                accountTenantId,
                assignmentRole,
                assignmentStatus,
                now,
                now
            );
        }
        return new StaffPrincipal(accountId, accountTenantId, tenantRole);
    }
}
