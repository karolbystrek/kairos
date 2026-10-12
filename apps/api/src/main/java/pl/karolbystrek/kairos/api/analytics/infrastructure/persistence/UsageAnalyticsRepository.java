package pl.karolbystrek.kairos.api.analytics.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class UsageAnalyticsRepository {
    private final JdbcTemplate database;

    public boolean record(UUID tenant, UUID location, String event, Timestamp time) {
        return Boolean.TRUE.equals(database.queryForObject("SELECT public.record_usage_action(?,?,?,?)",
                Boolean.class, tenant, location, event, time));
    }
}
