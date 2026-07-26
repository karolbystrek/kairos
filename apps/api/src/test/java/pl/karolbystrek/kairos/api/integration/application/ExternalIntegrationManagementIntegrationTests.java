package pl.karolbystrek.kairos.api.integration.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationAccessDeniedException;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationConflictException;
import pl.karolbystrek.kairos.api.integration.domain.ExternalIntegrationStatus;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClock;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClockConfiguration;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(MutableTestClockConfiguration.class)
class ExternalIntegrationManagementIntegrationTests
        extends RedisListenerIsolatedIntegrationTest {

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
        )).isInstanceOf(IntegrationAccessDeniedException.class);

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
    void issuesHashesRotatesAndRevokesApiKeyCredentials() {
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
        assertThat(jdbcTemplate.queryForObject(
                "SELECT secret_hash FROM api_key_versions WHERE id = ?",
                String.class,
                issued.version().id()
        )).isNotEqualTo(issued.secret());

        var initialPrincipal = authenticationService.authenticate(issued.secret());
        assertThat(initialPrincipal.integrationId()).isEqualTo(integration.id());
        assertThat(initialPrincipal.apiKeyId()).isEqualTo(issued.apiKey().id());
        assertThat(initialPrincipal.apiKeyVersionId()).isEqualTo(issued.version().id());

        clock.advance(Duration.ofMinutes(5));
        var rotated = apiKeyService.rotate(
                tenant.administrator(),
                issued.apiKey().id()
        );
        var versions = apiKeyService.listVersions(
                tenant.administrator(),
                issued.apiKey().id()
        );

        assertThat(authenticationService.authenticate(issued.secret()).apiKeyVersionId())
                .isEqualTo(issued.version().id());
        assertThat(authenticationService.authenticate(rotated.secret()).apiKeyVersionId())
                .isEqualTo(rotated.version().id());
        assertThat(versions)
                .filteredOn(version -> version.id().equals(issued.version().id()))
                .singleElement()
                .satisfies(version -> assertThat(version.validUntil())
                        .isEqualTo(clock.instant().plus(Duration.ofHours(24))));

        integrationService.changeStatus(
                tenant.administrator(),
                integration.id(),
                ExternalIntegrationStatus.DISABLED
        );
        assertThatThrownBy(() -> authenticationService.authenticate(rotated.secret()))
                .isInstanceOf(BadCredentialsException.class);

        integrationService.changeStatus(
                tenant.administrator(),
                integration.id(),
                ExternalIntegrationStatus.ENABLED
        );
        assertThat(authenticationService.authenticate(rotated.secret()).apiKeyId())
                .isEqualTo(issued.apiKey().id());

        apiKeyService.revoke(tenant.administrator(), issued.apiKey().id());
        assertThatThrownBy(() -> authenticationService.authenticate(rotated.secret()))
                .isInstanceOf(BadCredentialsException.class);
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
        assertThatThrownBy(() -> apiKeyService.rotate(
                tenant.administrator(),
                issued.apiKey().id()
        )).isInstanceOf(IntegrationConflictException.class);
    }
}
