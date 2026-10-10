package pl.karolbystrek.kairos.api.account.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.karolbystrek.kairos.api.account.application.exception.AccountInvitationUnavailableException;
import pl.karolbystrek.kairos.api.authentication.application.exception.RegistrationValidationException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.location.application.LocationService;
import pl.karolbystrek.kairos.api.location.domain.LocationStatus;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AccountInvitationConcurrencyIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private AccountInvitationService invitationService;

    @Autowired
    private LocationService locationService;

    private final JdbcTemplate jdbcTemplate = PostgresTestDatabase.ownerDatabase();

    @Test
    void atomicRedemptionRejectsAnEmailOutsideTheInvitation() throws Exception {
        var tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var email = "invited-" + UUID.randomUUID() + "@example.com";
        var created = invitationService.create(tenant.administrator(), tenant.firstLocationId(),
            AssignmentRole.OPERATOR, email);
        var differentEmail = "different-" + UUID.randomUUID() + "@example.com";

        assertThatThrownBy(() -> invitationService.redeem(hash(created.token()), differentEmail, differentEmail))
            .isInstanceOf(RegistrationValidationException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM account_invitations WHERE id = ?",
            String.class, created.invitation().id())).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ?",
            Integer.class, differentEmail)).isZero();
    }

    @Test
    void concurrentRedemptionCreatesExactlyOneAccountAndConsumesInvitationOnce() throws Exception {
        var tenantId = UUID.randomUUID();
        var locationId = UUID.randomUUID();
        var administratorId = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        jdbcTemplate.update(
            "INSERT INTO locations (id, tenant_id, name, normalized_name) VALUES (?, ?, ?, ?)",
            locationId,
            tenantId,
            "Concurrent location",
            "concurrent location"
        );
        jdbcTemplate.update(
            """
                INSERT INTO accounts (id, provider_subject, tenant_id, email, tenant_role, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'ADMIN', 'ENABLED', ?, ?)
                """,
            administratorId,
            UUID.randomUUID().toString(),
            tenantId,
            "concurrent-invitation-admin-" + administratorId + "@example.com",
            Timestamp.from(now),
            Timestamp.from(now)
        );
        var emailPrefix = "concurrent-redeemer-" + UUID.randomUUID();
        var created = invitationService.create(
            new StaffPrincipal(administratorId, tenantId, TenantRole.ADMIN),
            locationId,
            AssignmentRole.OPERATOR,
            emailPrefix + "@example.com"
        );
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> redeemAfterStart(
                start,
                created.token(),
                emailPrefix
            ));
            var second = executor.submit(() -> redeemAfterStart(
                start,
                created.token(),
                emailPrefix
            ));
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                .containsExactlyInAnyOrder(true, false);
        }

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE email LIKE ?",
            Integer.class,
            emailPrefix + "%"
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT state FROM account_invitations WHERE id = ?",
            String.class,
            created.invitation().id()
        )).isEqualTo("REDEEMED");
    }

    @Test
    void concurrentLocationDisableAndRedemptionFinishWithNoEnabledNewMember() throws Exception {
        var tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var email = "location-race-" + UUID.randomUUID() + "@example.com";
        var created = invitationService.create(tenant.administrator(), tenant.firstLocationId(), AssignmentRole.OPERATOR, email);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var redemption = executor.submit(() -> {
                start.await();
                try {
                    invitationService.redeem(hash(created.token()), email, email);
                } catch (AccountInvitationUnavailableException unavailable) {
                    assertThat(unavailable.reason()).isEqualTo(AccountInvitationUnavailableException.Reason.REVOKED);
                }
                return null;
            });
            var disable = executor.submit(() -> {
                start.await();
                locationService.updateStatus(tenant.administrator(), tenant.firstLocationId(),
                    LocationStatus.DISABLED);
                return null;
            });
            start.countDown();
            redemption.get(15, TimeUnit.SECONDS);
            disable.get(15, TimeUnit.SECONDS);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ? AND status = 'ENABLED'",
            Integer.class, email)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM account_invitations WHERE id = ?",
            String.class, created.invitation().id())).isIn("REDEEMED", "REVOKED");
    }

    private static String hash(String token) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean redeemAfterStart(CountDownLatch start, String token, String emailPrefix)
        throws Exception {
        start.await();
        try {
            invitationService.redeem(
                hash(token),
                emailPrefix + "@example.com",
                emailPrefix
            );
            return true;
        }
        catch (AccountInvitationUnavailableException exception) {
            assertThat(exception.reason())
                .isEqualTo(AccountInvitationUnavailableException.Reason.REDEEMED);
            return false;
        }
    }
}
