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
import pl.karolbystrek.kairos.api.menu.application.exception.MenuConflictException;
import pl.karolbystrek.kairos.api.menu.application.exception.MenuNotFoundException;
import pl.karolbystrek.kairos.api.menu.application.model.MenuView;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.application.exception.InvalidOrderRequestException;
import pl.karolbystrek.kairos.api.order.application.model.OrderItemSelection;
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
    void onlyTheOwningAdministratorManagesMenusAndEveryTenantMemberCanReadThem() {
        var tenantId = insertTenant();
        var administrator = insertAccount(tenantId, TenantRole.ADMIN);
        var location = locationService.create(administrator, "Kitchen");
        var manager = insertMember(tenantId, location.id(), AssignmentRole.MANAGER);
        var operator = insertMember(tenantId, location.id(), AssignmentRole.OPERATOR);
        var otherAdministrator = insertAccount(insertTenant(), TenantRole.ADMIN);

        var lunch = menuService.create(administrator, "  Lunch  ", List.of(" Burger ", "Frytki"));
        assertThat(lunch.name()).isEqualTo("Lunch");
        assertThat(lunch.items()).containsExactly("Burger", "Frytki");
        assertThatThrownBy(() -> menuService.create(administrator, "lunch", List.of("Zupa")))
            .isInstanceOf(MenuConflictException.class);
        for (var invalidItems : List.of(List.<String>of(), List.of("Burger", "burger"), List.of("Line\u2028break"))) {
            assertThatThrownBy(() -> menuService.create(administrator, "Invalid", invalidItems))
                .isInstanceOf(InvalidMenuRequestException.class);
        }
        for (var member : List.of(manager, operator)) {
            assertThatThrownBy(() -> menuService.create(member, "Breakfast", List.of("Jajecznica")))
                .isInstanceOf(StaffAccessDeniedException.class);
            assertThatThrownBy(() -> menuService.update(member, lunch.id(), "Lunch", List.of("Zupa")))
                .isInstanceOf(StaffAccessDeniedException.class);
            assertThat(menuService.list(member)).extracting(MenuView::id).containsExactly(lunch.id());
        }
        assertThat(menuService.list(otherAdministrator)).isEmpty();
        assertThatThrownBy(() -> menuService.update(otherAdministrator, lunch.id(), "Lunch", List.of("Zupa")))
            .isInstanceOf(MenuNotFoundException.class);
        assertThatThrownBy(() -> menuService.delete(otherAdministrator, lunch.id()))
            .isInstanceOf(MenuNotFoundException.class);

        var updated = menuService.update(administrator, lunch.id(), "Lunch", List.of("Zupa", "Burger"));
        assertThat(updated.items()).containsExactly("Zupa", "Burger");
        menuService.delete(administrator, lunch.id());
        assertThat(menuService.list(administrator)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM menu_items WHERE menu_id = ?", Integer.class, lunch.id()
        )).isZero();
    }

    @Test
    void managersChooseOnlyTheirLocationMenuAndAMenuInUseCannotBeDeleted() {
        var tenantId = insertTenant();
        var administrator = insertAccount(tenantId, TenantRole.ADMIN);
        var kitchen = locationService.create(administrator, "Kitchen");
        var bar = locationService.create(administrator, "Bar");
        var manager = insertMember(tenantId, kitchen.id(), AssignmentRole.MANAGER);
        var operator = insertMember(tenantId, kitchen.id(), AssignmentRole.OPERATOR);
        var lunch = menuService.create(administrator, "Lunch", List.of("Burger"));
        var dinner = menuService.create(administrator, "Dinner", List.of("Stek"));
        var foreignAdministrator = insertAccount(insertTenant(), TenantRole.ADMIN);
        var foreignMenu = menuService.create(foreignAdministrator, "Foreign", List.of("Sushi"));

        assertThat(menuService.findLocationMenu(operator, kitchen.id())).isEmpty();
        assertThat(menuService.selectLocationMenu(manager, kitchen.id(), lunch.id()))
            .map(MenuView::id).contains(lunch.id());
        assertThat(menuService.findLocationMenu(operator, kitchen.id()))
            .map(MenuView::id).contains(lunch.id());
        assertThatThrownBy(() -> menuService.selectLocationMenu(manager, bar.id(), lunch.id()))
            .isInstanceOf(MenuNotFoundException.class);
        assertThatThrownBy(() -> menuService.selectLocationMenu(operator, kitchen.id(), dinner.id()))
            .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> menuService.selectLocationMenu(manager, kitchen.id(), foreignMenu.id()))
            .isInstanceOf(MenuNotFoundException.class);
        assertThatThrownBy(() -> menuService.findLocationMenu(foreignAdministrator, kitchen.id()))
            .isInstanceOf(MenuNotFoundException.class);

        menuService.selectLocationMenu(administrator, bar.id(), lunch.id());
        menuService.selectLocationMenu(manager, kitchen.id(), dinner.id());
        assertThatThrownBy(() -> menuService.delete(administrator, lunch.id()))
            .isInstanceOf(MenuConflictException.class)
            .extracting(exception -> ((MenuConflictException) exception).getReason())
            .isEqualTo(MenuConflictException.Reason.IN_USE);

        locationService.updateStatus(administrator, bar.id(), LocationStatus.DISABLED);
        locationService.delete(administrator, bar.id());
        menuService.delete(administrator, lunch.id());
        assertThat(menuService.selectLocationMenu(manager, kitchen.id(), null)).isEmpty();
        menuService.delete(administrator, dinner.id());
        assertThat(menuService.list(administrator)).isEmpty();
    }

    @Test
    void ordersStoreChosenMenuItemsOnlyInTheirImmutableLabel() {
        var tenantId = insertTenant();
        var administrator = insertAccount(tenantId, TenantRole.ADMIN);
        var kitchen = locationService.create(administrator, "Kitchen");
        var operator = insertMember(tenantId, kitchen.id(), AssignmentRole.OPERATOR);
        var items = List.of(new OrderItemSelection("Frytki", 1), new OrderItemSelection("Burger", 2));

        assertThatThrownBy(() -> orderService.createOrder(operator, kitchen.id(), null, items))
            .isInstanceOf(InvalidOrderRequestException.class);
        var lunch = menuService.create(administrator, "Lunch", List.of("Burger", "Frytki", "Cola"));
        menuService.selectLocationMenu(administrator, kitchen.id(), lunch.id());

        var first = orderService.createOrder(operator, kitchen.id(), null, items);
        assertThat(first.label()).isEqualTo("1 · 2× Burger, Frytki");
        assertThat(orderService.createOrder(operator, kitchen.id(), "Stolik 4", List.of()).label())
            .isEqualTo("Stolik 4");
        assertThatThrownBy(() -> orderService.createOrder(operator, kitchen.id(), "Stolik 4", items))
            .isInstanceOf(InvalidOrderRequestException.class);
        assertThatThrownBy(() -> orderService.createOrder(
            operator, kitchen.id(), null, List.of(new OrderItemSelection("Pizza", 1))
        )).isInstanceOf(InvalidOrderRequestException.class);
        assertThatThrownBy(() -> orderService.createOrder(
            operator, kitchen.id(), null, List.of(new OrderItemSelection("Cola", 1), new OrderItemSelection("Cola", 2))
        )).isInstanceOf(InvalidOrderRequestException.class);

        var longItems = List.of("A".repeat(60), "B".repeat(60), "C".repeat(60), "D".repeat(60));
        var large = menuService.create(administrator, "Large", longItems);
        menuService.selectLocationMenu(administrator, kitchen.id(), large.id());
        assertThatThrownBy(() -> orderService.createOrder(
            operator, kitchen.id(), null, longItems.stream().map(item -> new OrderItemSelection(item, 1)).toList()
        )).isInstanceOf(InvalidOrderRequestException.class);

        menuService.update(administrator, lunch.id(), "Lunch", List.of("Zupa"));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT label FROM orders WHERE id = ?", String.class, first.id()
        )).isEqualTo("1 · 2× Burger, Frytki");
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
