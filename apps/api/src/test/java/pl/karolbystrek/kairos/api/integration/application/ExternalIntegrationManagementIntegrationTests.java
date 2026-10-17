package pl.karolbystrek.kairos.api.integration.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationConflictException;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationNotFoundException;
import pl.karolbystrek.kairos.api.integration.domain.ExternalIntegrationStatus;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClock;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ExternalIntegrationManagementIntegrationTests
        extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private ExternalIntegrationManagementService integrationService;

    @Autowired
    private ApiKeyManagementService apiKeyService;

    @Autowired
    private ApiKeyAuthenticationService authenticationService;

    private final JdbcTemplate jdbcTemplate = PostgresTestDatabase.ownerDatabase();

    @Autowired
    private JdbcTemplate runtimeDatabase;

    @Autowired
    private DatabaseAccessContext databaseAccess;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MutableTestClock clock;

    private IntegrationTestFixture.TenantFixture tenant;

    @BeforeEach
    void createFixture() {
        clock.setInstant(Instant.parse("2026-07-26T12:00:00Z"));
        tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
    }

    @Test
    void restrictsManagementToAdministratorsAndNormalizesUniqueNames() {
        var created = integrationService.create(
                tenant.administrator(),
                "  Dining Room POS  "
        );

        assertThat(created.name()).isEqualTo("Dining Room POS");
        assertThat(integrationService.list(tenant.administrator()))
                .extracting(view -> view.id())
                .containsExactly(created.id());
        assertThatThrownBy(() -> integrationService.create(
                tenant.administrator(),
                "dining room pos"
        )).isInstanceOf(IntegrationConflictException.class);
        assertThatThrownBy(() -> integrationService.create(
                tenant.manager(),
                "Manager-owned integration"
        )).isInstanceOf(StaffAccessDeniedException.class);

        var renamed = integrationService.rename(
                tenant.administrator(),
                created.id(),
                "Front Counter"
        );
        assertThat(renamed.name()).isEqualTo("Front Counter");

        integrationService.archive(tenant.administrator(), created.id());
        assertThat(integrationService.list(tenant.administrator())).isEmpty();
    }

    @Test
    void issuesHashesAndDeletesApiKeyCredentials() {
        var integration = integrationService.create(
                tenant.administrator(),
                "Kitchen POS"
        );
        var issued = apiKeyService.issue(
                tenant.administrator(),
                integration.id(),
                "Primary key",
                Set.of("orders:write"),
                Set.of(tenant.firstLocationId(), tenant.secondLocationId()),
                null
        );

        assertThat(issued.apiKey().scopes())
                .containsExactlyInAnyOrder("orders:read", "orders:write");
        assertThat(issued.apiKey().locationIds())
                .containsExactlyInAnyOrder(
                        tenant.firstLocationId(),
                        tenant.secondLocationId()
                );
        assertThat(issued.secret()).startsWith("kairos_key_v1." + issued.apiKey().id() + ".");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT secret_hash FROM api_keys WHERE id = ?",
                String.class,
                issued.apiKey().id()
        )).hasSize(64).isNotEqualTo(issued.secret());
        assertThat(issued.toString()).doesNotContain(issued.secret());

        var principal = authenticationService.authenticate(issued.secret());
        assertThat(principal.integrationId()).isEqualTo(integration.id());
        assertThat(principal.apiKeyId()).isEqualTo(issued.apiKey().id());

        integrationService.changeStatus(
                tenant.administrator(),
                integration.id(),
                ExternalIntegrationStatus.DISABLED
        );
        assertThatThrownBy(() -> authenticationService.authenticate(issued.secret()))
                .isInstanceOf(BadCredentialsException.class);

        integrationService.changeStatus(
                tenant.administrator(),
                integration.id(),
                ExternalIntegrationStatus.ENABLED
        );
        assertThat(authenticationService.authenticate(issued.secret()).apiKeyId())
                .isEqualTo(issued.apiKey().id());

        apiKeyService.delete(tenant.administrator(), issued.apiKey().id());
        assertThatThrownBy(() -> authenticationService.authenticate(issued.secret()))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(apiKeyService.list(tenant.administrator(), integration.id())).isEmpty();
        assertThatThrownBy(() -> apiKeyService.delete(tenant.administrator(), issued.apiKey().id()))
                .isInstanceOf(IntegrationNotFoundException.class);

        var replacement = apiKeyService.issue(
                tenant.administrator(),
                integration.id(),
                "primary KEY",
                Set.of("orders:read"),
                Set.of(tenant.firstLocationId()),
                null
        );
        assertThat(apiKeyService.list(tenant.administrator(), integration.id()))
                .extracting(view -> view.id())
                .containsExactly(replacement.apiKey().id());
    }

    @Test
    void recordsLastUseAtMostOncePerRefreshInterval() {
        var integration = integrationService.create(
                tenant.administrator(),
                "Bar POS"
        );
        var issued = apiKeyService.issue(
                tenant.administrator(),
                integration.id(),
                "Bar key",
                Set.of("orders:read"),
                Set.of(tenant.firstLocationId()),
                null
        );
        assertThat(issued.apiKey().lastUsedAt()).isNull();

        var firstUse = clock.instant();
        authenticationService.authenticate(issued.secret());
        assertThat(lastUsedAt(integration.id())).isEqualTo(firstUse);

        clock.advance(Duration.ofMinutes(30));
        authenticationService.authenticate(issued.secret());
        assertThat(lastUsedAt(integration.id())).isEqualTo(firstUse);

        clock.advance(Duration.ofMinutes(30));
        authenticationService.authenticate(issued.secret());
        assertThat(lastUsedAt(integration.id())).isEqualTo(clock.instant());
    }

    @Test
    void recordsUsageOnlyForTheBoundApiKey() {
        var integration = integrationService.create(
                tenant.administrator(),
                "Terrace POS"
        );
        var first = apiKeyService.issue(
                tenant.administrator(),
                integration.id(),
                "First key",
                Set.of("orders:read"),
                Set.of(tenant.firstLocationId()),
                null
        );
        var second = apiKeyService.issue(
                tenant.administrator(),
                integration.id(),
                "Second key",
                Set.of("orders:read"),
                Set.of(tenant.firstLocationId()),
                null
        );
        var now = Timestamp.from(clock.instant());

        assertThatThrownBy(() -> runtimeDatabase.queryForObject(
                "SELECT public.record_api_key_use(?, ?, ?)",
                Boolean.class,
                first.apiKey().id(),
                now,
                now
        )).isInstanceOf(DataAccessException.class);

        var principal = authenticationService.authenticate(first.secret());
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            databaseAccess.integration(principal);
            runtimeDatabase.queryForObject(
                    "SELECT public.record_api_key_use(?, ?, ?)",
                    Boolean.class,
                    second.apiKey().id(),
                    now,
                    now
            );
        })).isInstanceOf(DataAccessException.class);
    }

    @Test
    void enforcesImmutableExpirationDuringAuthentication() {
        var integration = integrationService.create(
                tenant.administrator(),
                "Expiring POS"
        );
        var issued = apiKeyService.issue(
                tenant.administrator(),
                integration.id(),
                "Short lived",
                Set.of("orders:read"),
                Set.of(tenant.firstLocationId()),
                clock.instant().plus(Duration.ofHours(1))
        );

        assertThat(authenticationService.authenticate(issued.secret()).apiKeyId())
                .isEqualTo(issued.apiKey().id());
        clock.advance(Duration.ofHours(2));
        assertThatThrownBy(() -> authenticationService.authenticate(issued.secret()))
                .isInstanceOf(BadCredentialsException.class);
    }

    private Instant lastUsedAt(UUID integrationId) {
        return apiKeyService.list(tenant.administrator(), integrationId).getFirst().lastUsedAt();
    }
}
