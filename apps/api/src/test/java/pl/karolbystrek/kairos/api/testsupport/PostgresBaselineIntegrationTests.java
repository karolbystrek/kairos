package pl.karolbystrek.kairos.api.testsupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SuppressWarnings({"rawtypes", "unchecked"})
class PostgresBaselineIntegrationTests extends RedisListenerIsolatedIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired SessionRepository sessions;

    @Test
    void migratesPostgres18AndPersistsSessions() {
        assertThat(database.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 18.");
        assertThat(database.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class)).isEqualTo(1);
        Session session = sessions.createSession();
        session.setAttribute("baseline", "persisted");
        sessions.save(session);
        try {
            assertThat((String) sessions.findById(session.getId()).getAttribute("baseline")).isEqualTo("persisted");
        } finally {
            sessions.deleteById(session.getId());
        }
    }
}
