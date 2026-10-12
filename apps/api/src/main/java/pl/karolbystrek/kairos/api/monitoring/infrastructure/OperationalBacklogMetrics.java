package pl.karolbystrek.kairos.api.monitoring.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class OperationalBacklogMetrics {
    private static final List<String> WORKERS = List.of("analytics", "webhook_fanout", "push_fanout",
            "webhook_delivery", "push_delivery");
    private final JdbcTemplate database;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Counter failures;
    private volatile Map<String, Backlog> snapshot = Map.of();
    private volatile double lastSuccess;
    private volatile boolean up;

    public OperationalBacklogMetrics(JdbcTemplate database, PlatformTransactionManager transactions,
                                     Clock clock, MeterRegistry registry) {
        this.database = database;
        this.clock = clock;
        failures = registry.counter("kairos.worker.failures", "worker", "backlog_snapshot");
        this.transaction = new TransactionTemplate(transactions);
        transaction.setReadOnly(true);
        transaction.setTimeout(3);
        for (var worker : WORKERS) {
            Gauge.builder("kairos.worker.backlog.pending", this, metrics -> metrics.pending(worker))
                    .tag("worker", worker).register(registry);
            Gauge.builder("kairos.worker.backlog.oldest.age", this, metrics -> metrics.oldestAge(worker))
                    .tag("worker", worker).baseUnit("seconds").register(registry);
        }
        Gauge.builder("kairos.worker.backlog.snapshot.timestamp", this, metrics -> metrics.lastSuccess)
                .baseUnit("seconds").register(registry);
        Gauge.builder("kairos.worker.backlog.snapshot.up", this, metrics -> metrics.up ? 1 : 0)
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${kairos.monitoring.backlog.poll-delay:30s}",
            initialDelayString = "${kairos.monitoring.backlog.initial-delay:5s}")
    public void refresh() {
        try {
            var next = transaction.execute(status -> {
                database.execute("SET LOCAL statement_timeout = '2s'");
                return database.query("SELECT worker, pending_count, oldest_age_seconds FROM public.operational_backlog()",
                        (row, index) -> Map.entry(row.getString("worker"),
                                new Backlog(row.getLong("pending_count"), row.getDouble("oldest_age_seconds"))))
                        .stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
            });
            snapshot = next;
            lastSuccess = clock.instant().getEpochSecond();
            up = true;
        } catch (RuntimeException exception) {
            up = false;
            failures.increment();
            log.warn("Operational backlog snapshot failed", exception);
        }
    }

    /** The last successful snapshot, its epoch-second time (0 when none), and whether the latest refresh succeeded. */
    public Snapshot snapshot() {
        return new Snapshot(snapshot, (long) lastSuccess, up);
    }

    public record Snapshot(Map<String, Backlog> workers, long observedAtEpochSecond, boolean current) {}

    private double pending(String worker) {
        var backlog = snapshot.get(worker);
        return backlog == null ? Double.NaN : backlog.pending();
    }

    private double oldestAge(String worker) {
        var backlog = snapshot.get(worker);
        return backlog == null ? Double.NaN : backlog.oldestAge();
    }

    public record Backlog(long pending, double oldestAge) {}
}
