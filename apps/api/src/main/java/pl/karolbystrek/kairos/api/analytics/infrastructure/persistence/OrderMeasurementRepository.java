package pl.karolbystrek.kairos.api.analytics.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrderMeasurementRepository {
    private final JdbcTemplate database;

    public void record(CustomerOrder order, Instant occurredAt, Long durationMs) {
        database.update("""
                INSERT INTO order_measurements (id, tenant_id, location_id, status, occurred_at, duration_ms)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), order.getLocation().getTenantId(), order.getLocation().getId(),
                order.getStatus().name(), Timestamp.from(occurredAt), durationMs);
    }

    public void aggregateAndConsume(UUID id) {
        var measurement = database.queryForObject("""
                SELECT tenant_id, location_id, status, occurred_at, duration_ms
                FROM order_measurements WHERE id = ?
                """, (row, index) -> new Measurement(row.getObject("tenant_id", UUID.class),
                row.getObject("location_id", UUID.class), row.getString("status"),
                row.getTimestamp("occurred_at").toInstant(), row.getObject("duration_ms", Long.class)), id);
        var duration = measurement.durationMs();
        var bucket = duration == null ? -1 : bucketFor(duration);
        database.update("""
                INSERT INTO order_hourly_analytics
                    (tenant_id, location_id, hour, status, bucket_version, transition_count,
                     duration_count, total_duration_ms, bucket_0, bucket_1, bucket_2, bucket_3,
                     bucket_4, bucket_5, bucket_6, bucket_7)
                VALUES (?, ?, ?, ?, 1, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, location_id, hour, status, bucket_version) DO UPDATE SET
                    transition_count = order_hourly_analytics.transition_count + 1,
                    duration_count = order_hourly_analytics.duration_count + EXCLUDED.duration_count,
                    total_duration_ms = order_hourly_analytics.total_duration_ms + EXCLUDED.total_duration_ms,
                    bucket_0 = order_hourly_analytics.bucket_0 + EXCLUDED.bucket_0,
                    bucket_1 = order_hourly_analytics.bucket_1 + EXCLUDED.bucket_1,
                    bucket_2 = order_hourly_analytics.bucket_2 + EXCLUDED.bucket_2,
                    bucket_3 = order_hourly_analytics.bucket_3 + EXCLUDED.bucket_3,
                    bucket_4 = order_hourly_analytics.bucket_4 + EXCLUDED.bucket_4,
                    bucket_5 = order_hourly_analytics.bucket_5 + EXCLUDED.bucket_5,
                    bucket_6 = order_hourly_analytics.bucket_6 + EXCLUDED.bucket_6,
                    bucket_7 = order_hourly_analytics.bucket_7 + EXCLUDED.bucket_7
                """, measurement.tenantId(), measurement.locationId(),
                Timestamp.from(measurement.occurredAt().truncatedTo(ChronoUnit.HOURS)), measurement.status(),
                duration == null ? 0 : 1, duration == null ? 0L : duration,
                bucket == 0 ? 1 : 0, bucket == 1 ? 1 : 0, bucket == 2 ? 1 : 0, bucket == 3 ? 1 : 0,
                bucket == 4 ? 1 : 0, bucket == 5 ? 1 : 0, bucket == 6 ? 1 : 0, bucket == 7 ? 1 : 0);
        database.update("DELETE FROM order_measurements WHERE id = ?", id);
    }

    private static int bucketFor(long durationMs) {
        var edges = new long[]{60_000, 120_000, 300_000, 600_000, 900_000, 1_800_000, 3_600_000};
        for (var index = 0; index < edges.length; index++) {
            if (durationMs < edges[index]) {
                return index;
            }
        }
        return edges.length;
    }

    private record Measurement(UUID tenantId, UUID locationId, String status, Instant occurredAt, Long durationMs) {
    }
}
