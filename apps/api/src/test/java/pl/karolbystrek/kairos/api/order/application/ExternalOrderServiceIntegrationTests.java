package pl.karolbystrek.kairos.api.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.integration.application.ApiKeyAuthenticationService;
import pl.karolbystrek.kairos.api.integration.application.ApiKeyManagementService;
import pl.karolbystrek.kairos.api.integration.application.ExternalIntegrationManagementService;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationAccessDeniedException;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyPrincipal;
import pl.karolbystrek.kairos.api.integration.application.model.ExternalIntegrationView;
import pl.karolbystrek.kairos.api.integration.application.model.IssuedApiKeyView;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClock;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClockConfiguration;
import pl.karolbystrek.kairos.api.order.application.exception.ExternalOrderConflictException;
import pl.karolbystrek.kairos.api.order.application.exception.InvalidOrderRequestException;
import pl.karolbystrek.kairos.api.order.application.exception.ResourceNotFoundException;
import pl.karolbystrek.kairos.api.order.domain.InvalidOrderTransitionException;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(MutableTestClockConfiguration.class)
class ExternalOrderServiceIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private ExternalOrderService orderService;

    @Autowired
    private ExternalIntegrationManagementService integrationService;

    @Autowired
    private ApiKeyManagementService apiKeyService;

    @Autowired
    private ApiKeyAuthenticationService authenticationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableTestClock clock;

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
                "   "
        );
        var replay = orderService.create(
                principal,
                tenant.firstLocationId(),
                null,
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
                "   "
        )).isInstanceOf(ExternalOrderConflictException.class);
        assertThatThrownBy(() -> orderService.create(
                principal,
                tenant.firstLocationId(),
                null,
                ""
        )).isInstanceOf(InvalidOrderRequestException.class);
        assertThatThrownBy(() -> orderService.create(
                principal,
                tenant.firstLocationId(),
                null,
                "é".repeat(128)
        )).isInstanceOf(InvalidOrderRequestException.class);
    }

    @Test
    void intersectsScopesAndLocationsWithoutDisclosingOtherOrders() {
        var created = orderService.create(
                principal,
                tenant.firstLocationId(),
                null,
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
                "read-only-create"
        )).isInstanceOf(IntegrationAccessDeniedException.class);
        assertThatThrownBy(() -> orderService.create(
                principal,
                tenant.secondLocationId(),
                null,
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
    void keepsSameStateCommandsIdempotentAndPreservesExactAuditIdentity() {
        var created = orderService.create(
                principal,
                tenant.firstLocationId(),
                "Counter 7",
                "audit-order"
        );
        var initialHistoryCount = countRows("order_history", created.order().id());
        var initialOutboxCount = countRows("webhook_outbox_events", created.order().id());

        var unchanged = orderService.updateStatus(
                principal,
                created.order().id(),
                OrderStatus.IN_PREPARATION
        );

        assertThat(unchanged.status()).isEqualTo(OrderStatus.IN_PREPARATION);
        assertThat(countRows("order_history", created.order().id()))
                .isEqualTo(initialHistoryCount);
        assertThat(countRows("webhook_outbox_events", created.order().id()))
                .isEqualTo(initialOutboxCount);

        var ready = orderService.updateStatus(
                principal,
                created.order().id(),
                OrderStatus.READY
        );
        assertThat(ready.status()).isEqualTo(OrderStatus.READY);
        assertThat(countRows("order_history", created.order().id()))
                .isEqualTo(initialHistoryCount + 1);
        assertThat(countRows("webhook_outbox_events", created.order().id()))
                .isEqualTo(initialOutboxCount + 1);
        assertThat(jdbcTemplate.queryForList(
                """
                SELECT initiator_id
                FROM order_history
                WHERE order_id = ?
                ORDER BY id
                """,
                java.util.UUID.class,
                created.order().id()
        )).containsOnly(integration.id());
        assertThat(jdbcTemplate.queryForList(
                """
                SELECT initiator_api_key_id
                FROM order_history
                WHERE order_id = ?
                ORDER BY id
                """,
                java.util.UUID.class,
                created.order().id()
        )).containsOnly(issuedKey.apiKey().id());
        assertThat(jdbcTemplate.queryForList(
                """
                SELECT initiator_api_key_version_id
                FROM order_history
                WHERE order_id = ?
                ORDER BY id
                """,
                java.util.UUID.class,
                created.order().id()
        )).containsOnly(issuedKey.version().id());

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
                "page-1"
        ).order();
        clock.advance(Duration.ofSeconds(1));
        var second = orderService.create(
                principal,
                tenant.firstLocationId(),
                null,
                "page-2"
        ).order();
        clock.advance(Duration.ofSeconds(1));
        var third = orderService.create(
                principal,
                tenant.firstLocationId(),
                null,
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

    private long countRows(String table, java.util.UUID orderId) {
        var query = "SELECT COUNT(*) FROM " + table + " WHERE order_id = ?";
        return jdbcTemplate.queryForObject(query, Long.class, orderId);
    }
}
