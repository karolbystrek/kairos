package pl.karolbystrek.kairos.api.menu.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.location.application.LocationService;
import pl.karolbystrek.kairos.api.location.domain.LocationStatus;
import pl.karolbystrek.kairos.api.menu.application.exception.InvalidMenuRequestException;
import pl.karolbystrek.kairos.api.menu.application.exception.MenuNotFoundException;
import pl.karolbystrek.kairos.api.menu.application.model.MenuView;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.application.exception.InvalidOrderRequestException;
import pl.karolbystrek.kairos.api.order.domain.OrderItem;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class MenuServiceIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private MenuService menuService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private LocationService locationService;

    private final JdbcTemplate jdbcTemplate = PostgresTestDatabase.ownerDatabase();

    @Test
    void administratorsAndTheLocationManagerOwnIndependentLocationMenus() {
        var tenantId = insertTenant();
        var administrator = insertAccount(tenantId, TenantRole.ADMIN);
        var kitchen = locationService.create(administrator, "Kitchen", null, "UTC");
        var bar = locationService.create(administrator, "Bar", null, "UTC");
        var manager = insertMember(tenantId, kitchen.id(), AssignmentRole.MANAGER);
        var operator = insertMember(tenantId, kitchen.id(), AssignmentRole.OPERATOR);
        var foreignAdministrator = insertAccount(insertTenant(), TenantRole.ADMIN);

        assertThat(menuService.findLocationMenu(operator, kitchen.id())).isEmpty();
        assertThat(menuService.saveLocationMenu(manager, kitchen.id(), List.of(" Burger ", "Frytki")).items())
            .containsExactly("Burger", "Frytki");
        assertThat(menuService.findLocationMenu(operator, kitchen.id()))
            .map(MenuView::items).contains(List.of("Burger", "Frytki"));
        for (var invalidItems : List.of(List.<String>of(), List.of("Burger", "burger"), List.of("Line break"))) {
            assertThatThrownBy(() -> menuService.saveLocationMenu(manager, kitchen.id(), invalidItems))
                .isInstanceOf(InvalidMenuRequestException.class);
        }
        assertThatThrownBy(() -> menuService.saveLocationMenu(operator, kitchen.id(), List.of("Zupa")))
            .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> menuService.deleteLocationMenu(operator, kitchen.id()))
            .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> menuService.saveLocationMenu(manager, bar.id(), List.of("Piwo")))
            .isInstanceOf(MenuNotFoundException.class);
        assertThatThrownBy(() -> menuService.saveLocationMenu(foreignAdministrator, kitchen.id(), List.of("Sushi")))
            .isInstanceOf(MenuNotFoundException.class);
        assertThatThrownBy(() -> menuService.findLocationMenu(foreignAdministrator, kitchen.id()))
            .isInstanceOf(MenuNotFoundException.class);

        // A copied menu is independent of its source.
        var copied = menuService.findLocationMenu(administrator, kitchen.id()).orElseThrow().items();
        menuService.saveLocationMenu(administrator, bar.id(), copied);
        menuService.saveLocationMenu(manager, kitchen.id(), List.of("Zupa", "Burger"));
        assertThat(menuService.findLocationMenu(administrator, bar.id()))
            .map(MenuView::items).contains(List.of("Burger", "Frytki"));

        menuService.deleteLocationMenu(manager, kitchen.id());
        assertThat(menuService.findLocationMenu(operator, kitchen.id())).isEmpty();
        menuService.deleteLocationMenu(manager, kitchen.id());

        locationService.updateStatus(administrator, bar.id(), LocationStatus.DISABLED);
        locationService.delete(administrator, bar.id());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM menu_items WHERE location_id IN (?, ?)", Integer.class, kitchen.id(), bar.id()
        )).isZero();
    }

    @Test
    void ordersStoreChosenMenuItemsAsASnapshotBesideTheirLabel() {
        var tenantId = insertTenant();
        var administrator = insertAccount(tenantId, TenantRole.ADMIN);
        var kitchen = locationService.create(administrator, "Kitchen", null, "UTC");
        var operator = insertMember(tenantId, kitchen.id(), AssignmentRole.OPERATOR);
        var items = List.of(new OrderItem("Frytki", 1), new OrderItem("Burger", 2));

        assertThatThrownBy(() -> orderService.createOrder(operator, kitchen.id(), null, items))
            .isInstanceOf(InvalidOrderRequestException.class);
        menuService.saveLocationMenu(administrator, kitchen.id(), List.of("Burger", "Frytki", "Cola"));

        var first = orderService.createOrder(operator, kitchen.id(), "Stolik 4", items);
        assertThat(first.label()).isEqualTo("Stolik 4");
        assertThat(first.items()).isEqualTo(items);
        var second = orderService.createOrder(operator, kitchen.id(), null, List.of());
        assertThat(second.label()).isNull();
        assertThat(second.items()).isEmpty();
        assertThat(List.of(first.number(), second.number())).containsExactly(1L, 2L);
        assertThatThrownBy(() -> orderService.createOrder(
            operator, kitchen.id(), null, List.of(new OrderItem("Pizza", 1))
        )).isInstanceOf(InvalidOrderRequestException.class);
        assertThatThrownBy(() -> orderService.createOrder(
            operator, kitchen.id(), null, List.of(new OrderItem("Cola", 1), new OrderItem("Cola", 2))
        )).isInstanceOf(InvalidOrderRequestException.class);

        menuService.saveLocationMenu(administrator, kitchen.id(), List.of("Zupa"));
        menuService.deleteLocationMenu(administrator, kitchen.id());
        assertThat(jdbcTemplate.queryForList(
            "SELECT name || ':' || quantity FROM order_items WHERE order_id = ? ORDER BY position",
            String.class,
            first.id()
        )).containsExactly("Frytki:1", "Burger:2");
    }

    private UUID insertTenant() {
        var tenantId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        return tenantId;
    }

    private StaffPrincipal insertAccount(UUID tenantId, TenantRole tenantRole) {
        var accountId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
            """
            INSERT INTO accounts (id, provider_subject, tenant_id, email, tenant_role, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'ENABLED', ?, ?)
            """,
            accountId,
            UUID.randomUUID().toString(),
            tenantId,
            "menu-test-" + accountId + "@example.com",
            tenantRole.name(),
            now,
            now
        );
        return new StaffPrincipal(accountId, tenantId, tenantRole);
    }

    private StaffPrincipal insertMember(UUID tenantId, UUID locationId, AssignmentRole role) {
        var member = insertAccount(tenantId, TenantRole.MEMBER);
        var now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
            """
            INSERT INTO location_assignments (account_id, location_id, tenant_id, role, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            member.accountId(),
            locationId,
            tenantId,
            role.name(),
            now,
            now
        );
        return member;
    }
}
