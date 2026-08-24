package pl.karolbystrek.kairos.api.account.application;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.exception.FinalPlatformOperatorConfirmationRequiredException;
import pl.karolbystrek.kairos.api.account.application.model.PlatformOperatorPrincipal;
import pl.karolbystrek.kairos.api.authentication.application.AuthenticationSessionService;
import pl.karolbystrek.kairos.api.tenant.application.TenantRegistrationInvitationService;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PlatformOperatorAccountServiceIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private PlatformOperatorAccountService operatorAccountService;

    @Autowired
    private TenantRegistrationInvitationService invitationService;

    @Autowired
    private AuthenticationSessionService sessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void provisionsAndLifecycleManagesAnOperatorWithAtomicShutdownEffects() {
        jdbcTemplate.update(
            "UPDATE accounts SET status = 'DISABLED' WHERE kind = 'PLATFORM_OPERATOR'"
        );
        var suffix = java.util.UUID.randomUUID().toString();
        var account = operatorAccountService.provision(
            "  Platform." + suffix.toUpperCase() + "  ",
            "PLATFORM." + suffix + "@EXAMPLE.COM",
            "Correct-Horse-12"
        );
        var principal = new PlatformOperatorPrincipal(account.getId());
        sessionService.start(principal);
        var invitation = invitationService.create(principal, "Deployment customer");

        assertThat(account.getUsername()).isEqualTo("platform." + suffix);
        assertThat(account.getEmail()).isEqualTo("platform." + suffix + "@example.com");
        assertThat(account.getTenantId()).isNull();
        assertThat(account.getTenantRole()).isNull();

        assertThatThrownBy(() -> operatorAccountService.disable(account.getUsername(), false))
            .isInstanceOf(FinalPlatformOperatorConfirmationRequiredException.class);

        operatorAccountService.disable(account.getUsername(), true);
        entityManager.flush();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM accounts WHERE id = ?",
            String.class,
            account.getId()
        )).isEqualTo("DISABLED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sessions WHERE account_id = ? AND revoked_at IS NULL",
            Integer.class,
            account.getId()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT state FROM tenant_registration_invitations WHERE id = ?",
            String.class,
            invitation.invitation().id()
        )).isEqualTo("REVOKED");

        operatorAccountService.enable(account.getUsername());
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM accounts WHERE id = ?",
            String.class,
            account.getId()
        )).isEqualTo("ENABLED");
    }
}
