package pl.karolbystrek.kairos.api.account.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.karolbystrek.kairos.api.account.application.exception.AccountInvitationUnavailableException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AccountInvitationConcurrencyIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private AccountInvitationService invitationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentRedemptionCreatesExactlyOneAccountAndConsumesInvitationOnce() throws Exception {
        var tenantId = UUID.randomUUID();
        var locationId = UUID.randomUUID();
        var administratorId = UUID.randomUUID();
        var now = Instant.now();
        jdbcTemplate.update("INSERT INTO tenants (id) VALUES (?)", tenantId);
        jdbcTemplate.update(
            "INSERT INTO locations (id, tenant_id, name, normalized_name, live_normalized_name) VALUES (?, ?, ?, ?, ?)",
            locationId,
            tenantId,
            "Concurrent location",
            "concurrent location",
            "concurrent location"
        );
        jdbcTemplate.update(
            """
                INSERT INTO accounts (id, provider_subject, tenant_id, email, tenant_role, status, created_at, updated_at) VALUES (?, CAST(RANDOM_UUID() AS VARCHAR), ?, ?, 'ADMIN', 'ENABLED', ?, ?)
                """,
            administratorId,
            tenantId,
            "concurrent-invitation-admin-" + administratorId + "@example.com",
            now,
            now
        );
        var created = invitationService.create(
            new StaffPrincipal(administratorId, tenantId, TenantRole.ADMIN),
            locationId,
            AssignmentRole.OPERATOR
        );
        var emailPrefix = "concurrent-redeemer-" + UUID.randomUUID();
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> redeemAfterStart(
                start,
                created.token(),
                emailPrefix + "-first"
            ));
            var second = executor.submit(() -> redeemAfterStart(
                start,
                created.token(),
                emailPrefix + "-second"
            ));
            start.countDown();

            assertThat(java.util.List.of(first.get(), second.get()))
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

    private boolean redeemAfterStart(CountDownLatch start, String token, String emailPrefix)
        throws Exception {
        start.await();
        try {
            invitationService.redeem(
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
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
