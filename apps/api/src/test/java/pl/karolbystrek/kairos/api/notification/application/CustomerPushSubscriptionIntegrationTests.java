package pl.karolbystrek.kairos.api.notification.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClock;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClockConfiguration;
import pl.karolbystrek.kairos.api.notification.application.exception.CustomerPushEnrollmentLimitException;
import pl.karolbystrek.kairos.api.notification.application.exception.InvalidCustomerPushSubscriptionException;
import pl.karolbystrek.kairos.api.notification.application.model.CustomerPushSubscriptionInput;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushDeliveryStatus;
import pl.karolbystrek.kairos.api.notification.infrastructure.security.VapidKeyLoader;
import pl.karolbystrek.kairos.api.order.application.OrderService;
import pl.karolbystrek.kairos.api.order.application.model.StaffOrderView;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(MutableTestClockConfiguration.class)
class CustomerPushSubscriptionIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private CustomerPushSubscriptionService subscriptionService;

    @Autowired
    private CustomerPushOutboxFanoutService fanoutService;

    @Autowired
    private CustomerPushDeliveryClaimService claimService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableTestClock clock;

    @TestBean(enforceOverride = true)
    private StringRedisTemplate stringRedisTemplate;

    private final List<String> endpointHashes = new ArrayList<>();
    private IntegrationTestFixture.TenantFixture tenant;
    private int endpointSequence;

    @BeforeEach
    void createFixture() {
        clock.setInstant(Instant.parse("2026-07-26T12:00:00Z"));
        tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
    }

    @AfterEach
    void removeCommittedFixture() {
        var tenantId = tenant.tenantId();
        jdbcTemplate.update(
                """
                DELETE FROM customer_push_deliveries
                WHERE order_id IN (
                    SELECT orders.id
                    FROM orders
                    JOIN locations ON locations.id = orders.location_id
                    WHERE locations.tenant_id = ?
                )
                """,
                tenantId
        );
        jdbcTemplate.update(
                """
                DELETE FROM customer_push_enrollments
                WHERE order_id IN (
                    SELECT orders.id
                    FROM orders
                    JOIN locations ON locations.id = orders.location_id
                    WHERE locations.tenant_id = ?
                )
                """,
                tenantId
        );
        endpointHashes.forEach(hash -> jdbcTemplate.update(
                "DELETE FROM customer_push_subscriptions WHERE endpoint_hash = ?",
                hash
        ));
        jdbcTemplate.update(
                "DELETE FROM order_outbox_events WHERE tenant_id = ?",
                tenantId
        );
        jdbcTemplate.update(
                """
                DELETE FROM order_history
                WHERE order_id IN (
                    SELECT orders.id
                    FROM orders
                    JOIN locations ON locations.id = orders.location_id
                    WHERE locations.tenant_id = ?
                )
                """,
                tenantId
        );
        jdbcTemplate.update(
                """
                DELETE FROM orders
                WHERE location_id IN (
                    SELECT id FROM locations WHERE tenant_id = ?
                )
                """,
                tenantId
        );
        jdbcTemplate.update(
                "DELETE FROM location_assignments WHERE tenant_id = ?",
                tenantId
        );
        jdbcTemplate.update("DELETE FROM accounts WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM locations WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenantId);
        endpointHashes.clear();
    }

    @Test
    void storesEncryptedCapabilitiesAndReconcilesActiveOrdersIdempotently() {
        var order = createOrder();
        var input = newSubscription();

        subscriptionService.reconcile(
                input,
                List.of(order.trackingReference(), UUID.randomUUID())
        );
        subscriptionService.reconcile(input, List.of(order.trackingReference()));

        var encryptedEndpoint = jdbcTemplate.queryForObject(
                "SELECT encrypted_endpoint FROM customer_push_subscriptions",
                byte[].class
        );
        var encryptedAuth = jdbcTemplate.queryForObject(
                "SELECT encrypted_auth_secret FROM customer_push_subscriptions",
                byte[].class
        );
        assertThat(encryptedEndpoint)
                .isNotEqualTo(input.endpoint().getBytes(StandardCharsets.UTF_8));
        assertThat(encryptedAuth)
                .isNotEqualTo(Base64.getUrlDecoder().decode(input.auth()));
        assertThat(count("customer_push_subscriptions")).isEqualTo(1);
        assertThat(count("customer_push_enrollments")).isEqualTo(1);
    }

    @Test
    void requiresTheCompleteCapabilityForAnExistingEndpoint() {
        var order = createOrder();
        var input = newSubscription();
        subscriptionService.reconcile(input, List.of(order.trackingReference()));
        var mismatched = new CustomerPushSubscriptionInput(
                input.endpoint(),
                input.p256dh(),
                base64Url(randomBytes(16)),
                null
        );

        assertThatThrownBy(() -> subscriptionService.reconcile(
                mismatched,
                List.of(order.trackingReference())
        )).isInstanceOf(InvalidCustomerPushSubscriptionException.class);
        assertThat(count("customer_push_subscriptions")).isEqualTo(1);
        assertThat(count("customer_push_enrollments")).isEqualTo(1);
    }

    @Test
    void replacesAndDisablesACompleteSubscription() {
        var order = createOrder();
        var previous = newSubscription();
        var current = newSubscription();
        subscriptionService.reconcile(previous, List.of(order.trackingReference()));

        subscriptionService.replace(
                previous,
                current,
                List.of(order.trackingReference())
        );

        assertThat(subscriptionCount(previous)).isZero();
        assertThat(subscriptionCount(current)).isEqualTo(1);
        assertThat(count("customer_push_enrollments")).isEqualTo(1);

        subscriptionService.disable(current);

        assertThat(count("customer_push_subscriptions")).isZero();
        assertThat(count("customer_push_enrollments")).isZero();
    }

    @Test
    void serializesAndEnforcesThePerOrderEnrollmentLimit() {
        var order = createOrder();
        for (var index = 0; index < 10; index++) {
            subscriptionService.reconcile(
                    newSubscription(),
                    List.of(order.trackingReference())
            );
        }

        assertThatThrownBy(() -> subscriptionService.reconcile(
                newSubscription(),
                List.of(order.trackingReference())
        )).isInstanceOf(CustomerPushEnrollmentLimitException.class);
        assertThat(count("customer_push_enrollments")).isEqualTo(10);
    }

    @Test
    void cancelsAnExpiredClaimAfterItsSubscriptionWasRemoved() {
        var order = createOrder();
        var input = newSubscription();
        subscriptionService.reconcile(input, List.of(order.trackingReference()));
        orderService.updateStatus(
                tenant.administrator(),
                order.id(),
                OrderStatus.READY
        );
        while (fanoutService.fanOutAvailable() > 0) {
            // Drain both the creation marker and READY event.
        }
        var claimed = claimService.claimAvailable();
        assertThat(claimed).hasSize(1);

        subscriptionService.disable(input);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT subscription_id FROM customer_push_deliveries",
                UUID.class
        )).isNull();
        clock.advance(Duration.ofSeconds(31));

        assertThat(claimService.claimAvailable()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM customer_push_deliveries",
                String.class
        )).isEqualTo(CustomerPushDeliveryStatus.CANCELED.name());
    }

    private static StringRedisTemplate stringRedisTemplate() {
        return new NoOpStringRedisTemplate();
    }

    private StaffOrderView createOrder() {
        return orderService.createOrder(
                tenant.administrator(),
                tenant.firstLocationId(),
                null
        );
    }

    private CustomerPushSubscriptionInput newSubscription() {
        try {
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            var keyPair = generator.generateKeyPair();
            var endpoint = "http://127.0.0.1:"
                    + (10_000 + endpointSequence++)
                    + "/push";
            endpointHashes.add(sha256(endpoint));
            return new CustomerPushSubscriptionInput(
                    endpoint,
                    base64Url(VapidKeyLoader.encodeUncompressed(
                            (ECPublicKey) keyPair.getPublic()
                    )),
                    base64Url(randomBytes(16)),
                    null
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Could not generate a customer Push test capability",
                    exception
            );
        }
    }

    private long subscriptionCount(CustomerPushSubscriptionInput input) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM customer_push_subscriptions
                WHERE endpoint_hash = ?
                """,
                Long.class,
                sha256(input.endpoint())
        );
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Long.class
        );
    }

    private static byte[] randomBytes(int length) {
        var value = new byte[length];
        new SecureRandom().nextBytes(value);
        return value;
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
            ));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class NoOpStringRedisTemplate extends StringRedisTemplate {

        @Override
        public void afterPropertiesSet() {
        }

        @Override
        public Long convertAndSend(String destination, Object message) {
            return 0L;
        }
    }
}
