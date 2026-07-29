package pl.karolbystrek.kairos.api.order.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.karolbystrek.kairos.api.order.domain.OrderOutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OrderOutboxEventRepository extends JpaRepository<OrderOutboxEvent, UUID> {

    @Query(value = """
            SELECT *
            FROM order_outbox_events
            WHERE webhook_fanout_completed_at IS NULL
            ORDER BY occurred_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OrderOutboxEvent> findAvailableForWebhookFanout(@Param("batchSize") int batchSize);

    @Query(value = """
            SELECT *
            FROM order_outbox_events
            WHERE push_fanout_completed_at IS NULL
            ORDER BY occurred_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OrderOutboxEvent> findAvailableForPushFanout(@Param("batchSize") int batchSize);
}
