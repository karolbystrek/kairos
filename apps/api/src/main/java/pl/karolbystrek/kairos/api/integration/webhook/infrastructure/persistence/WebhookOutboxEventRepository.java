package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookOutboxEvent;

import java.util.List;
import java.util.UUID;

public interface WebhookOutboxEventRepository extends JpaRepository<WebhookOutboxEvent, UUID> {

    @Query(value = """
            SELECT *
            FROM webhook_outbox_events
            WHERE fanout_completed_at IS NULL
            ORDER BY occurred_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookOutboxEvent> findAvailableForFanout(@Param("batchSize") int batchSize);
}
