package pl.karolbystrek.kairos.api.monitoring.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalBacklogFailureTests {
    @Test
    @SuppressWarnings("unchecked")
    void failedRefreshKeepsLastSnapshotAndReportsFailure() throws Exception {
        var database = mock(JdbcTemplate.class);
        var row = mock(ResultSet.class);
        when(row.getString("worker")).thenReturn("analytics");
        when(row.getLong("pending_count")).thenReturn(3L);
        when(row.getDouble("oldest_age_seconds")).thenReturn(20.0);
        when(database.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> List.of(((RowMapper<?>) invocation.getArgument(1)).mapRow(row, 0)))
                .thenThrow(new QueryTimeoutException("snapshot timed out"));
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new OperationalBacklogMetrics(database, mock(PlatformTransactionManager.class),
                    Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC), registry);
            metrics.refresh();
            metrics.refresh();
            assertThat(registry.get("kairos.worker.backlog.pending").tag("worker", "analytics").gauge().value()).isEqualTo(3);
            assertThat(registry.get("kairos.worker.backlog.oldest.age").tag("worker", "analytics").gauge().value()).isEqualTo(20);
            assertThat(registry.get("kairos.worker.backlog.snapshot.timestamp").gauge().value()).isEqualTo(100);
            assertThat(registry.get("kairos.worker.backlog.snapshot.up").gauge().value()).isZero();
            assertThat(registry.get("kairos.worker.failures").tag("worker", "backlog_snapshot").counter().count()).isEqualTo(1);
        } finally {
            registry.close();
        }
    }
}
