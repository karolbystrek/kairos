package pl.karolbystrek.kairos.api.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;

public final class PostgresTestDatabase {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:18-alpine");

    private PostgresTestDatabase() {}

    public static void start() {
        try {
            DATABASE.start();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Backend verification requires Docker for disposable PostgreSQL 18 tests", exception);
        }
        var owner = ownerDatabase();
        owner.execute("CREATE ROLE kairos_owner LOGIN PASSWORD 'owner-test' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS");
        owner.execute("CREATE ROLE kairos_runtime LOGIN PASSWORD 'runtime-test' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS");
        owner.execute("GRANT CREATE, CONNECT ON DATABASE test TO kairos_owner");
        owner.execute("ALTER SCHEMA public OWNER TO kairos_owner");
        owner.execute("REVOKE TEMPORARY ON DATABASE test FROM PUBLIC");
        System.setProperty("spring.flyway.placeholders.runtimeUser", "kairos_runtime");
        System.setProperty("spring.datasource.hikari.maximum-pool-size", "4");
        System.setProperty("spring.datasource.url", DATABASE.getJdbcUrl());
        System.setProperty("spring.datasource.username", "kairos_runtime");
        System.setProperty("spring.datasource.password", "runtime-test");
        System.setProperty("spring.flyway.url", DATABASE.getJdbcUrl());
        System.setProperty("spring.flyway.user", "kairos_owner");
        System.setProperty("spring.flyway.password", "owner-test");
    }

    public static void stop() {
        DATABASE.stop();
    }

    public static String adminUrl(String name) {
        if (!name.matches("[a-z_]+")) throw new IllegalArgumentException("Invalid test database name");
        return DATABASE.getJdbcUrl().replace("/test?", "/" + name + "?");
    }

    public static Container.ExecResult execute(Path directory, String... arguments) throws Exception {
        DATABASE.copyFileToContainer(MountableFile.forHostPath(directory), "/tmp/rls-bootstrap");
        return DATABASE.execInContainer(arguments);
    }

    public static JdbcTemplate ownerDatabase() {
        return new JdbcTemplate(new DriverManagerDataSource(
                DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()));
    }
}
