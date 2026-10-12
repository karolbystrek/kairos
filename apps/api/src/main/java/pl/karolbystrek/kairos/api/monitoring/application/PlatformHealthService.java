package pl.karolbystrek.kairos.api.monitoring.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;
import pl.karolbystrek.kairos.api.monitoring.infrastructure.OperationalBacklogMetrics;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Sanitized current signals only: no hosts, credentials, history or tenant/location labels. */
@Service
@RequiredArgsConstructor
public class PlatformHealthService {
    private final OperationalBacklogMetrics backlog;
    private final RedisConnectionFactory redis;
    private final Clock clock;

    public enum Status { UP, DOWN, UNKNOWN }
    public record Worker(String worker, long pending, double oldestAgeSeconds) {}
    public record Health(Instant observedAt, Status database, Status redis, Instant backlogObservedAt,
                         boolean backlogCurrent, List<Worker> workers) {}

    /** Callers authorize through the database first, so reaching this method means PostgreSQL answered. */
    public Health current() {
        var snapshot = backlog.snapshot();
        var workers = snapshot.workers().entrySet().stream()
                .map(entry -> new Worker(entry.getKey(), entry.getValue().pending(), entry.getValue().oldestAge()))
                .sorted(Comparator.comparing(Worker::worker)).toList();
        return new Health(clock.instant(), Status.UP, redisStatus(),
                snapshot.observedAtEpochSecond() == 0 ? null : Instant.ofEpochSecond(snapshot.observedAtEpochSecond()),
                snapshot.current(), workers);
    }

    // shortcut: a hung ping keeps a common-pool thread until Lettuce's command timeout; use a dedicated bounded executor if health polling grows.
    private Status redisStatus() {
        try {
            var reply = CompletableFuture.supplyAsync(() -> {
                try (var connection = redis.getConnection()) {
                    return connection.ping();
                }
            }).get(2, TimeUnit.SECONDS);
            return "PONG".equalsIgnoreCase(reply) ? Status.UP : Status.DOWN;
        } catch (TimeoutException exception) {
            return Status.UNKNOWN;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Status.UNKNOWN;
        } catch (Exception exception) {
            return Status.DOWN;
        }
    }
}
