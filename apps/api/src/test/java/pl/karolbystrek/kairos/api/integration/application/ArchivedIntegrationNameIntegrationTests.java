package pl.karolbystrek.kairos.api.integration.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.karolbystrek.kairos.api.integration.testsupport.IntegrationTestFixture;
import pl.karolbystrek.kairos.api.testsupport.PostgresTestDatabase;
import pl.karolbystrek.kairos.api.testsupport.RedisListenerIsolatedIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ArchivedIntegrationNameIntegrationTests extends RedisListenerIsolatedIntegrationTest {

    @Autowired
    private ExternalIntegrationManagementService integrationService;

    private final JdbcTemplate jdbcTemplate = PostgresTestDatabase.ownerDatabase();

    @Test
    void archivedIntegrationFreesItsName() {
        var tenant = new IntegrationTestFixture(jdbcTemplate).createTenant();
        var archived = integrationService.create(tenant.administrator(), "Front Counter");
        integrationService.archive(tenant.administrator(), archived.id());

        var recreated = integrationService.create(tenant.administrator(), "front counter");

        assertThat(recreated.id()).isNotEqualTo(archived.id());
    }
}
