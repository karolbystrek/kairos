package pl.karolbystrek.kairos.api.integration.webhook.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.integration.application.ExternalIntegrationManagementService;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationAccessDeniedException;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationConflictException;
import pl.karolbystrek.kairos.api.integration.domain.ExternalIntegrationStatus;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClock;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClockConfiguration;
import pl.karolbystrek.kairos.api.order.domain.OrderEventType;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscriptionStatus;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(MutableTestClockConfiguration.class)
class WebhookSubscriptionIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private WebhookSubscriptionManagementService subscriptionService;

    @Autowired
    private WebhookOutboxFanoutService fanoutService;

    @Autowired
    private ExternalIntegrationManagementService integrationService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MutableTestClock clock;

    private IntegrationTestFixture.TenantFixture tenant;
    private java.util.UUID integrationId;

    @BeforeEach
    void createFixture() {
        clock.setInstant(Instant.parse("2026-07-26T12:00:00Z"));
        tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        integrationId = integrationService.create(
                tenant.administrator(),
                "Webhook integration"
        ).id();
    }

    @Test
    void managesNormalizedSubscriptionsAndOneTimeSigningSecrets() {
        var issued = subscriptionService.create(
                tenant.administrator(),
                integrationId,
                "  Order Events  ",
                "http://127.0.0.1:9080/events",
                Set.of(tenant.firstLocationId()),
                Set.of(OrderEventType.ORDER_CREATED, OrderEventType.ORDER_READY)
        );

        assertThat(issued.subscription().name()).isEqualTo("Order Events");
        assertThat(issued.subscription().status())
                .isEqualTo(WebhookSubscriptionStatus.DISABLED);
        assertThat(issued.subscription().locationIds())
                .containsExactly(tenant.firstLocationId());
        assertThat(issued.subscription().eventTypes())
                .containsExactlyInAnyOrder(
                        OrderEventType.ORDER_CREATED,
                        OrderEventType.ORDER_READY
                );
        var encrypted = jdbcTemplate.queryForObject(
                """
                SELECT encrypted_secret
                FROM webhook_signing_secret_versions
                WHERE id = ?
                """,
                byte[].class,
                issued.subscription().signingSecretVersions().getFirst().id()
        );
        assertThat(encrypted)
                .isNotEqualTo(issued.signingSecret().getBytes(StandardCharsets.UTF_8));
        assertThat(subscriptionService.list(tenant.administrator(), integrationId))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.locationIds()).containsExactly(tenant.firstLocationId());
                    assertThat(view.signingSecretVersions()).hasSize(1);
                });

        assertThatThrownBy(() -> subscriptionService.create(
                tenant.administrator(),
                integrationId,
                "order events",
                "http://127.0.0.1:9081/events",
                Set.of(tenant.firstLocationId()),
                Set.of(OrderEventType.ORDER_CREATED)
        )).isInstanceOf(IntegrationConflictException.class);
        assertThatThrownBy(() -> subscriptionService.list(
                tenant.manager(),
                integrationId
        )).isInstanceOf(IntegrationAccessDeniedException.class);
    }

    @Test
    void rotatesWithOverlapAndAllowsImmediatePredecessorRetirement() {
        var issued = createSubscription(Set.of(OrderEventType.ORDER_CREATED));
        clock.advance(Duration.ofMinutes(10));
        var replacement = subscriptionService.rotateSigningSecret(
                tenant.administrator(),
                issued.subscription().id()
        );
        var afterRotation = subscriptionService.list(
                tenant.administrator(),
                integrationId
        ).getFirst();
        var predecessor = afterRotation.signingSecretVersions().stream()
                .filter(version -> !version.id().equals(replacement.version().id()))
                .findFirst()
                .orElseThrow();

        assertThat(predecessor.validUntil())
                .isEqualTo(clock.instant().plus(Duration.ofHours(24)));
        assertThat(predecessor.retiredAt()).isNull();

        var retired = subscriptionService.retireSigningSecret(
                tenant.administrator(),
                issued.subscription().id(),
                predecessor.id()
        );
        assertThat(retired.retiredAt()).isEqualTo(clock.instant());
    }

    @Test
    void filtersFanoutAndSerializesACompleteSafeCloudEventSnapshot() throws Exception {
        var issued = createSubscription(Set.of(OrderEventType.ORDER_READY));
        subscriptionService.changeStatus(
                tenant.administrator(),
                issued.subscription().id(),
                WebhookSubscriptionStatus.ENABLED
        );

        var order = orderService.createOrder(
                tenant.administrator(),
                tenant.firstLocationId(),
                "Pickup 4"
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_outbox_events WHERE order_id = ?",
                Long.class,
                order.id()
        )).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        orderService.updateStatus(
                tenant.administrator(),
                order.id(),
                OrderStatus.READY
        );
        assertThat(fanoutService.fanOutAvailable()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_deliveries WHERE subscription_id = ?",
                Long.class,
                issued.subscription().id()
        )).isEqualTo(1);

        var payload = jdbcTemplate.queryForObject(
                """
                SELECT payload
                FROM webhook_deliveries
                WHERE subscription_id = ?
                """,
                String.class,
                issued.subscription().id()
        );
        var event = objectMapper.readTree(payload);
        assertThat(event.get("specversion").asText()).isEqualTo("1.0");
        assertThat(event.get("source").asText()).isEqualTo("urn:kairos:orders");
        assertThat(event.get("type").asText()).isEqualTo("order.ready");
        assertThat(event.get("subject").asText()).isEqualTo("orders/" + order.id());
        assertThat(event.get("data").get("id").asText()).isEqualTo(order.id().toString());
        assertThat(event.get("data").get("locationId").asText())
                .isEqualTo(tenant.firstLocationId().toString());
        assertThat(event.get("data").get("label").asText()).isEqualTo("Pickup 4");
        assertThat(payload)
                .doesNotContain("trackingReference")
                .doesNotContain("idempotency");
    }

    @Test
    void doesNotReplayEventsMissedWhileIntegrationWasDisabled() {
        var issued = createSubscription(Set.of(OrderEventType.ORDER_CREATED));
        subscriptionService.changeStatus(
                tenant.administrator(),
                issued.subscription().id(),
                WebhookSubscriptionStatus.ENABLED
        );
        integrationService.changeStatus(
                tenant.administrator(),
                integrationId,
                ExternalIntegrationStatus.DISABLED
        );
        clock.advance(Duration.ofSeconds(1));
        orderService.createOrder(
                tenant.administrator(),
                tenant.firstLocationId(),
                null
        );
        clock.advance(Duration.ofSeconds(1));
        integrationService.changeStatus(
                tenant.administrator(),
                integrationId,
                ExternalIntegrationStatus.ENABLED
        );

        assertThat(fanoutService.fanOutAvailable()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_deliveries WHERE subscription_id = ?",
                Long.class,
                issued.subscription().id()
        )).isZero();
    }

    private pl.karolbystrek.kairos.api.integration.webhook.application.model.IssuedWebhookSubscriptionView
    createSubscription(Set<OrderEventType> eventTypes) {
        return subscriptionService.create(
                tenant.administrator(),
                integrationId,
                "Order events",
                "http://127.0.0.1:9080/events",
                Set.of(tenant.firstLocationId()),
                eventTypes
        );
    }
}
