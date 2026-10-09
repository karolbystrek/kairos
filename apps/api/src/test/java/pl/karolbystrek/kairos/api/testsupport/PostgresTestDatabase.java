package pl.karolbystrek.kairos.api.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class PostgresTestDatabase {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:18-alpine");

    private PostgresTestDatabase() {}

    public static void start() {
        try {
            DATABASE.start();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Backend verification requires Docker for disposable PostgreSQL 18 tests", exception);
        }
        System.setProperty("spring.datasource.url", DATABASE.getJdbcUrl());
        System.setProperty("spring.datasource.username", DATABASE.getUsername());
        System.setProperty("spring.datasource.password", DATABASE.getPassword());
        System.setProperty("spring.flyway.url", DATABASE.getJdbcUrl());
        System.setProperty("spring.flyway.user", DATABASE.getUsername());
        System.setProperty("spring.flyway.password", DATABASE.getPassword());
    }

    public static void stop() {
        DATABASE.stop();
    }

    public static JdbcTemplate ownerDatabase() {
        return new JdbcTemplate(new DriverManagerDataSource(
                DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()));
    }
}
