package pl.karolbystrek.kairos.api.tenant.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.karolbystrek.kairos.api.account.application.PlatformOperatorAccountService;
import pl.karolbystrek.kairos.api.account.application.model.PlatformOperatorPrincipal;
import pl.karolbystrek.kairos.api.tenant.application.exception.TenantRegistrationInvitationUnavailableException;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TenantRegistrationConcurrencyIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private PlatformOperatorAccountService operatorAccountService;

    @Autowired
    private TenantRegistrationInvitationService invitationService;

    @Autowired
    private TenantRegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentRedemptionCreatesExactlyOneTenantAdministratorAndSession() throws Exception {
        var suffix = java.util.UUID.randomUUID().toString();
        var operator = operatorAccountService.provision(
            "concurrency.operator." + suffix,
            "concurrency.operator." + suffix + "@example.com",
            "Correct-Horse-12"
        );
        var invitation = invitationService.create(
            new PlatformOperatorPrincipal(operator.getId()),
            "Concurrent redemption"
        );
        var tenantCount = count("tenants");
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> redeem(start, invitation.token(), "first." + suffix));
            var second = executor.submit(() -> redeem(start, invitation.token(), "second." + suffix));
            start.countDown();

            assertThat(java.util.List.of(first.get(), second.get()))
                .containsExactlyInAnyOrder(true, false);
        }

        assertThat(count("tenants")).isEqualTo(tenantCount + 1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE kind = 'TENANT_ACCOUNT' AND username IN (?, ?)",
            Integer.class,
            "first." + suffix,
            "second." + suffix
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sessions s JOIN accounts a ON a.id = s.account_id WHERE a.username IN (?, ?)",
            Integer.class,
            "first." + suffix,
            "second." + suffix
        )).isOne();
    }

    private boolean redeem(CountDownLatch start, String token, String username) throws Exception {
        start.await();
        try {
            registrationService.register(
                null,
                token,
                username,
                username + "@example.com",
                "Correct-Horse-12"
            );
            return true;
        }
        catch (TenantRegistrationInvitationUnavailableException exception) {
            return false;
        }
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
