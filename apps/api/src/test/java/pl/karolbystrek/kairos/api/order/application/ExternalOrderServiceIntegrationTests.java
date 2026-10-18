package pl.karolbystrek.kairos.api.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.integration.application.ApiKeyAuthenticationService;
import pl.karolbystrek.kairos.api.integration.application.ApiKeyManagementService;
import pl.karolbystrek.kairos.api.integration.application.ExternalIntegrationManagementService;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationAccessDeniedException;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyPrincipal;
import pl.karolbystrek.kairos.api.integration.application.model.ExternalIntegrationView;
import pl.karolbystrek.kairos.api.integration.application.model.IssuedApiKeyView;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClock;
import pl.karolbystrek.kairos.api.menu.application.MenuService;
import pl.karolbystrek.kairos.api.menu.application.model.MenuView;
import pl.karolbystrek.kairos.api.order.application.exception.ExternalOrderConflictException;
import pl.karolbystrek.kairos.api.order.application.exception.InvalidOrderRequestException;
import pl.karolbystrek.kairos.api.order.application.exception.ResourceNotFoundException;
import pl.karolbystrek.kairos.api.order.domain.InvalidOrderTransitionException;
import pl.karolbystrek.kairos.api.order.domain.OrderItem;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ExternalOrderServiceIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private ExternalOrderService orderService;

    @Autowired
    private ExternalIntegrationManagementService integrationService;

    @Autowired
    private ApiKeyManagementService apiKeyService;

    @Autowired
    private ApiKeyAuthenticationService authenticationService;

    private final JdbcTemplate jdbcTemplate = PostgresTestDatabase.ownerDatabase();

    @Autowired
    private MutableTestClock clock;

    @Autowired
    private MenuService menuService;

    @Autowired
    private JdbcTemplate runtime;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DatabaseAccessContext databaseAccess;

    private IntegrationTestFixture.TenantFixture tenant;
    private ExternalIntegrationView integration;
    private IssuedApiKeyView issuedKey;
    private ApiKeyPrincipal principal;

    @BeforeEach
    void createFixture() {
        clock.setInstant(Instant.parse("2026-07-26T12:00:00Z"));
        tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        integration = integrationService.create(tenant.administrator(), "Order adapter");
        issuedKey = apiKeyService.issue(
                tenant.administrator(),
                integration.id(),
                "Writable orders",
                Set.of("orders:write"),
                Set.of(tenant.firstLocationId()),
                null
        );
        principal = authenticationService.authenticate(issuedKey.secret());
    }

    @Test
    void treatsIdempotencyKeysAsOpaqueAndRejectsOnlyEmptyOrOversizedValues() {
        var created = orderService.create(
                principal,
                tenant.firstLocationId(),
                null,
                List.of(),
                "   "
        );
        var replay = orderService.create(
                principal,
                tenant.firstLocationId(),
                null,
                List.of(),
                "   "
        );

        assertThat(created.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.order().id()).isEqualTo(created.order().id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE external_idempotency_key = ?",
                Long.class,
                "   "
        )).isEqualTo(1);
        assertThatThrownBy(() -> orderService.create(
                principal,
                tenant.firstLocationId(),
                "Different input",
                List.of(),
                "   "
        )).isInstanceOf(ExternalOrderConflictException.class);
        assertThatThrownBy(() -> orderService.create(
                principal,
                tenant.firstLocationId(),
                null,
                List.of(),
                ""
        )).isInstanceOf(InvalidOrderRequestException.class);
        assertThatThrownBy(() -> orderService.create(
                principal,
                tenant.firstLocationId(),
                null,
                List.of(),
                "é".repeat(128)
        )).isInstanceOf(InvalidOrderRequestException.class);
    }

    @Test
    void intersectsScopesAndLocationsWithoutDisclosingOtherOrders() {
        var created = orderService.create(
                principal,
                tenant.firstLocationId(),
                null,
                List.of(),
                "visible"
        );
        var readOnlyKey = apiKeyService.issue(
                tenant.administrator(),
                integration.id(),
                "Read only",
                Set.of("orders:read"),
                Set.of(tenant.firstLocationId()),
                null
        );
        var readOnlyPrincipal = authenticationService.authenticate(readOnlyKey.secret());

        assertThat(orderService.find(readOnlyPrincipal, created.order().id()).id())
                .isEqualTo(created.order().id());
        assertThatThrownBy(() -> orderService.create(
                readOnlyPrincipal,
                tenant.firstLocationId(),
                null,
                List.of(),
                "read-only-create"
        )).isInstanceOf(IntegrationAccessDeniedException.class);
        assertThatThrownBy(() -> orderService.create(
                principal,
                tenant.secondLocationId(),
                null,
                List.of(),
                "unassigned-location"
        )).isInstanceOf(IntegrationAccessDeniedException.class);

        var otherTenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var otherIntegration = integrationService.create(
                otherTenant.administrator(),
                "Other tenant"
        );
        var otherKey = apiKeyService.issue(
                otherTenant.administrator(),
                otherIntegration.id(),
                "Other key",
                Set.of("orders:write"),
                Set.of(otherTenant.firstLocationId()),
                null
        );
        var otherPrincipal = authenticationService.authenticate(otherKey.secret());
        var hidden = orderService.create(
                otherPrincipal,
                otherTenant.firstLocationId(),
                null,
                List.of(),
                "hidden"
        );

        assertThatThrownBy(() -> orderService.find(principal, hidden.order().id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> orderService.updateStatus(
                principal,
                hidden.order().id(),
                OrderStatus.READY
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void keepsSameStateCommandsIdempotent() {
        var created = orderService.create(
                principal,
                tenant.firstLocationId(),
                "Counter 7",
                List.of(),
                "audit-order"
        );
        var initialOutboxCount = countRows("order_outbox_events", created.order().id());

        var unchanged = orderService.updateStatus(
                principal,
                created.order().id(),
                OrderStatus.IN_PREPARATION
        );

        assertThat(unchanged.status()).isEqualTo(OrderStatus.IN_PREPARATION);
        assertThat(countRows("order_outbox_events", created.order().id()))
                .isEqualTo(initialOutboxCount);

        var ready = orderService.updateStatus(
                principal,
                created.order().id(),
                OrderStatus.READY
        );
        assertThat(ready.status()).isEqualTo(OrderStatus.READY);
        assertThat(countRows("order_outbox_events", created.order().id()))
                .isEqualTo(initialOutboxCount + 1);

        assertThatThrownBy(() -> orderService.updateStatus(
                principal,
                created.order().id(),
                OrderStatus.IN_PREPARATION
        )).isInstanceOf(InvalidOrderTransitionException.class);
    }

    @Test
    void paginatesInDescendingCreationOrderAndAppliesFilters() {
        var first = orderService.create(
                principal,
                tenant.firstLocationId(),
                null,
                List.of(),
                "page-1"
        ).order();
        clock.advance(Duration.ofSeconds(1));
        var second = orderService.create(
                principal,
                tenant.firstLocationId(),
                null,
                List.of(),
                "page-2"
        ).order();
        clock.advance(Duration.ofSeconds(1));
        var third = orderService.create(
                principal,
                tenant.firstLocationId(),
                null,
                List.of(),
                "page-3"
        ).order();
        orderService.updateStatus(principal, second.id(), OrderStatus.READY);

        var firstPage = orderService.list(principal, null, null, null, 2);
        var secondPage = orderService.list(
                principal,
                null,
                null,
                firstPage.nextCursor(),
                2
        );
        var readyPage = orderService.list(
                principal,
                tenant.firstLocationId(),
                OrderStatus.READY,
                null,
                null
        );

        assertThat(firstPage.items()).extracting(item -> item.id())
                .containsExactly(third.id(), second.id());
        assertThat(firstPage.nextCursor()).isNotBlank();
        assertThat(secondPage.items()).extracting(item -> item.id())
                .containsExactly(first.id());
        assertThat(secondPage.nextCursor()).isNull();
        assertThat(readyPage.items()).extracting(item -> item.id())
                .containsExactly(second.id());
        assertThatThrownBy(() -> orderService.list(
                principal,
                tenant.secondLocationId(),
                null,
                null,
                null
        )).isInstanceOf(IntegrationAccessDeniedException.class);
    }

    @Test
    void pagesAcrossLocationsWithoutDuplicatesOrGaps() {
        var both = authenticationService.authenticate(apiKeyService.issue(
                tenant.administrator(),
                integration.id(),
                "Two locations",
                Set.of("orders:write"),
                Set.of(tenant.firstLocationId(), tenant.secondLocationId()),
                null
        ).secret());
        var expected = new ArrayList<UUID>();
        for (var i = 0; i < 5; i++) {
            var location = i % 2 == 0 ? tenant.firstLocationId() : tenant.secondLocationId();
            expected.addFirst(orderService.create(both, location, null, List.of(), "multi-" + i).order().id());
            clock.advance(Duration.ofSeconds(1));
        }

        var seen = new ArrayList<UUID>();
        String cursor = null;
        do {
            var page = orderService.list(both, null, null, cursor, 2);
            page.items().forEach(item -> seen.add(item.id()));
            cursor = page.nextCursor();
        } while (cursor != null);

        assertThat(seen).containsExactlyElementsOf(expected);
    }

    @Test
    void writeOnlyPersistedScopeAllowsCommandsButDoesNotAllowReadEndpoints() {
        jdbcTemplate.update("DELETE FROM api_key_scopes WHERE api_key_id = ? AND scope = 'ORDERS_READ'", principal.apiKeyId());
        var writeOnly = authenticationService.authenticate(issuedKey.secret());
        var created = orderService.create(writeOnly, tenant.firstLocationId(), null, List.of(), "write-only");
        assertThat(created.order().status()).isEqualTo(OrderStatus.IN_PREPARATION);
        assertThat(orderService.create(writeOnly, tenant.firstLocationId(), null, List.of(), "write-only").replayed()).isTrue();
        assertThat(orderService.updateStatus(writeOnly, created.order().id(), OrderStatus.READY).status()).isEqualTo(OrderStatus.READY);
        assertThatThrownBy(() -> orderService.find(writeOnly, created.order().id()))
            .isInstanceOf(IntegrationAccessDeniedException.class);
        assertThatThrownBy(() -> orderService.list(writeOnly, null, null, null, null))
            .isInstanceOf(IntegrationAccessDeniedException.class);
    }

    @Test
    void rejectsPrincipalAfterItsPersistedLocationGrantChanges() {
        var created = orderService.create(principal, tenant.firstLocationId(), null, List.of(), "stale-grant").order();
        jdbcTemplate.update("DELETE FROM api_key_location_access WHERE api_key_id = ? AND location_id = ?",
            principal.apiKeyId(), tenant.firstLocationId());
        assertThatThrownBy(() -> orderService.find(principal, created.id()))
            .isInstanceOf(IntegrationAccessDeniedException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?",
            String.class, created.id())).isEqualTo("IN_PREPARATION");
    }

    @Test
    void createsOrdersWithMenuItemsFromTheLocationMenuItsKeyCanRead() {
        var locationId = tenant.firstLocationId();
        var items = List.of(new OrderItem("Burger", 2));
        assertThat(menuService.findLocationMenu(principal, locationId)).isEmpty();
        assertThatThrownBy(() -> orderService.create(principal, locationId, null, items, "no-menu"))
                .isInstanceOf(InvalidOrderRequestException.class);

        menuService.saveLocationMenu(tenant.administrator(), locationId, List.of("Burger", "Frytki"));
        assertThat(menuService.findLocationMenu(principal, locationId).map(MenuView::items))
                .contains(List.of("Burger", "Frytki"));
        assertThatThrownBy(() -> menuService.findLocationMenu(principal, tenant.secondLocationId()))
                .isInstanceOf(IntegrationAccessDeniedException.class);
        menuService.saveLocationMenu(tenant.administrator(), tenant.secondLocationId(), List.of("Piwo"));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            databaseAccess.integration(principal);
            assertThat(runtime.queryForList("SELECT name FROM menu_items ORDER BY position", String.class))
                    .containsExactly("Burger", "Frytki");
            assertThat(runtime.queryForObject("SELECT count(*) FROM location_menus", Integer.class)).isEqualTo(1);
        });

        var created = orderService.create(principal, locationId, "Okienko", items, "with-items").order();
        assertThat(created.number()).isEqualTo(1);
        assertThat(created.items()).isEqualTo(items);
        assertThat(orderService.find(principal, created.id()).items()).isEqualTo(items);
        assertThat(orderService.create(principal, locationId, "Okienko", items, "with-items").replayed()).isTrue();
        assertThatThrownBy(() -> orderService.create(
                principal, locationId, "Okienko", List.of(new OrderItem("Frytki", 1)), "with-items"
        )).isInstanceOf(ExternalOrderConflictException.class);
        assertThatThrownBy(() -> orderService.create(
                principal, locationId, null, List.of(new OrderItem("Pizza", 1)), "stale-item"
        )).isInstanceOf(InvalidOrderRequestException.class);
    }

    private long countRows(String table, UUID orderId) {
        var query = "SELECT COUNT(*) FROM " + table + " WHERE order_id = ?";
        return jdbcTemplate.queryForObject(query, Long.class, orderId);
    }
}
