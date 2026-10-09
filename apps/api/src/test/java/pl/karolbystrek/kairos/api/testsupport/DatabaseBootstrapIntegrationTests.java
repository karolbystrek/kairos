package pl.karolbystrek.kairos.api.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class DatabaseBootstrapIntegrationTests {
    @TempDir Path directory;

    @Test
    void freshPostgresInitializationCreatesRolesBeforeFlywayConnects() {
        var script=Path.of("../../deployment/postgres/bootstrap.sh").toAbsolutePath().normalize();
        try (var postgres=new PostgreSQLContainer("postgres:18-alpine")
                .withEnv("KAIROS_DB_OWNER_USER","initial_owner")
                .withEnv("KAIROS_DB_OWNER_PASSWORD","owner-test")
                .withEnv("KAIROS_DB_RUNTIME_USER","initial_runtime")
                .withEnv("KAIROS_DB_RUNTIME_PASSWORD","runtime-test")
                .withCopyFileToContainer(MountableFile.forHostPath(script),"/docker-entrypoint-initdb.d/10-kairos-roles.sh")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(),"initial_owner","owner-test")
                .placeholders(java.util.Map.of("runtimeUser","initial_runtime"))
                .locations("classpath:db/migration").load().migrate();
            var runtime=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),"initial_runtime","runtime-test"));
            new pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseIsolationVerifier(runtime).run(null);
            assertThat(runtime.queryForObject("SELECT count(*) FROM tenants",Integer.class)).isZero();
            assertThatThrownBy(() -> runtime.update("INSERT INTO tenants(id) VALUES(gen_random_uuid())"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
    }

    @Test
    void provisionsRestrictedRolesIdempotentlyAndRejectsPrivilegeEscalation() throws Exception {
        var admin=PostgresTestDatabase.ownerDatabase();
        admin.execute("CREATE DATABASE bootstrap_test");
        var root=Path.of("../..").toAbsolutePath().normalize();
        Files.copy(root.resolve("deployment/postgres/bootstrap.sh"),directory.resolve("bootstrap.sh"));
        var first=bootstrap("bootstrap_runtime");
        assertThat(first.getExitCode()).as(first.getStdout()+first.getStderr()).isZero();
        var second=bootstrap("bootstrap_runtime");
        assertThat(second.getExitCode()).as(second.getStdout()+second.getStderr()).isZero();
        var runtime=new JdbcTemplate(new DriverManagerDataSource(PostgresTestDatabase.adminUrl("bootstrap_test"),"bootstrap_runtime","runtime-test"));
        assertThat(runtime.queryForObject("SELECT rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb FROM pg_roles WHERE rolname=current_user",Boolean.class)).isFalse();
        assertThatThrownBy(() -> runtime.execute("CREATE TABLE public.forbidden(id uuid)")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> runtime.execute("CREATE TEMP TABLE forbidden(id uuid)")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> runtime.execute("SET ROLE bootstrap_owner")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        admin.execute("ALTER ROLE bootstrap_runtime BYPASSRLS");
        try {
            var rejected=bootstrap("bootstrap_runtime");
            assertThat(rejected.getExitCode()).isNotZero();
            assertThat(rejected.getStderr()).contains("Unexpected privileged database role").doesNotContain("owner-test","runtime-test");
            assertThat(admin.queryForObject("SELECT rolbypassrls FROM pg_roles WHERE rolname='bootstrap_runtime'",Boolean.class)).isTrue();
        } finally {
            admin.execute("ALTER ROLE bootstrap_runtime NOBYPASSRLS");
        }
        var invalid=bootstrap("bad-role");
        assertThat(invalid.getExitCode()).isNotZero();
    }

    private org.testcontainers.containers.Container.ExecResult bootstrap(String role) throws Exception {
        return PostgresTestDatabase.execute(directory,"env","PGHOST=localhost","POSTGRES_DB=bootstrap_test","POSTGRES_USER=test","POSTGRES_PASSWORD=test",
            "KAIROS_DB_OWNER_USER=bootstrap_owner","KAIROS_DB_OWNER_PASSWORD=owner-test","KAIROS_DB_RUNTIME_USER="+role,
            "KAIROS_DB_RUNTIME_PASSWORD=runtime-test","sh","/tmp/rls-bootstrap/bootstrap.sh");
    }
}
