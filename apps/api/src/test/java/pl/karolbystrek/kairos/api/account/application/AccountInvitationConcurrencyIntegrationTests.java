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
                INSERT INTO accounts (
                    id, tenant_id, username, email, password_hash,
                    tenant_role, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'ADMIN', 'ENABLED', ?, ?)
                """,
            administratorId,
            tenantId,
            "concurrent-invitation-admin-" + administratorId,
            "concurrent-invitation-admin-" + administratorId + "@example.com",
            "fixture-password-hash",
            now,
            now
        );
        var created = invitationService.create(
            new StaffPrincipal(administratorId, tenantId, TenantRole.ADMIN),
            locationId,
            AssignmentRole.OPERATOR
        );
        var usernamePrefix = "concurrent-redeemer-" + UUID.randomUUID();
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> redeemAfterStart(
                start,
                created.token(),
                usernamePrefix + "-first"
            ));
            var second = executor.submit(() -> redeemAfterStart(
                start,
                created.token(),
                usernamePrefix + "-second"
            ));
            start.countDown();

            assertThat(java.util.List.of(first.get(), second.get()))
                .containsExactlyInAnyOrder(true, false);
        }

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE username LIKE ?",
            Integer.class,
            usernamePrefix + "%"
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT state FROM account_invitations WHERE id = ?",
            String.class,
            created.invitation().id()
        )).isEqualTo("REDEEMED");
    }

    private boolean redeemAfterStart(CountDownLatch start, String token, String username)
        throws InterruptedException {
        start.await();
        try {
            invitationService.redeem(
                null,
                token,
                username,
                username + "@example.com",
                "Secure-Password-12"
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
