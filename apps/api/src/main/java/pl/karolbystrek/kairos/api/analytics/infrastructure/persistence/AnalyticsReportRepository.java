package pl.karolbystrek.kairos.api.analytics.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Reads hourly aggregates; the report view and usage row-level security also limit every query to the bound reporting scope. */
@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AnalyticsReportRepository {
    private static final String SCOPE = "(?::uuid IS NULL OR tenant_id=?) AND (?::uuid IS NULL OR location_id=?)";
    private final JdbcTemplate database;

    public record OrderRow(LocalDate day, String status, long transitions, long samples, long durationMs) {}
    public record LocationRow(UUID locationId, String name, String status, long transitions) {}
    public record UsageRow(String event, long actions) {}

    public boolean tenantLocationExists(UUID tenantId, UUID locationId) {
        return Boolean.TRUE.equals(database.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM locations WHERE id=? AND tenant_id=?)", Boolean.class, locationId, tenantId));
    }

    // Whole UTC hours belong to the local date of their start instant.
    public List<OrderRow> orders(UUID tenantId, UUID locationId, Instant from, Instant to, String zone) {
        return database.query("""
                SELECT (hour AT TIME ZONE ?)::date AS day, status, sum(transition_count) AS transitions,
                    sum(duration_count) AS samples, sum(total_duration_ms) AS duration
                FROM order_hourly_reports WHERE hour >= ? AND hour < ? AND %s
                GROUP BY 1, 2
                """.formatted(SCOPE), (row, index) -> new OrderRow(row.getObject("day", LocalDate.class),
                row.getString("status"), row.getLong("transitions"), row.getLong("samples"), row.getLong("duration")),
                zone, Timestamp.from(from), Timestamp.from(to), tenantId, tenantId, locationId, locationId);
    }

    public List<LocationRow> locations(UUID tenantId, Instant from, Instant to) {
        return database.query("""
                SELECT a.location_id, CASE WHEN l.status <> 'ARCHIVED' THEN l.name END AS name, a.status,
                    sum(a.transition_count) AS transitions
                FROM order_hourly_reports a LEFT JOIN locations l ON l.id=a.location_id
                WHERE a.tenant_id=? AND a.hour >= ? AND a.hour < ?
                GROUP BY 1, 2, 3
                """, (row, index) -> new LocationRow(row.getObject("location_id", UUID.class), row.getString("name"),
                row.getString("status"), row.getLong("transitions")),
                tenantId, Timestamp.from(from), Timestamp.from(to));
    }

    public List<UsageRow> usage(UUID tenantId, UUID locationId, Instant from, Instant to) {
        return database.query("""
                SELECT event, sum(action_count) AS actions FROM usage_hourly_analytics
                WHERE hour >= ? AND hour < ? AND %s GROUP BY event
                """.formatted(SCOPE), (row, index) -> new UsageRow(row.getString("event"), row.getLong("actions")),
                Timestamp.from(from), Timestamp.from(to), tenantId, tenantId, locationId, locationId);
    }

    /** Earliest aggregated hour in scope; earlier days have no collected data rather than zero activity. */
    public Instant firstRecordedHour(UUID tenantId, UUID locationId) {
        var first = database.queryForObject("""
                SELECT LEAST((SELECT min(hour) FROM order_hourly_reports WHERE %1$s),
                    (SELECT min(hour) FROM usage_hourly_analytics WHERE %1$s))
                """.formatted(SCOPE), Timestamp.class,
                tenantId, tenantId, locationId, locationId, tenantId, tenantId, locationId, locationId);
        return first == null ? null : first.toInstant();
    }
}
