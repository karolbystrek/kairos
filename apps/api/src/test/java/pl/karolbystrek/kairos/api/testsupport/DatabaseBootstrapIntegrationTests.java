package pl.karolbystrek.kairos.api.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class DatabaseBootstrapIntegrationTests {
    @TempDir Path directory;

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
