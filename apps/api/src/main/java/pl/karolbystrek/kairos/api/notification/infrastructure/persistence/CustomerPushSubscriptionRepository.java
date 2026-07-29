package pl.karolbystrek.kairos.api.notification.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushSubscription;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPushSubscriptionRepository
        extends JpaRepository<CustomerPushSubscription, UUID> {

    Optional<CustomerPushSubscription> findByEndpointHash(String endpointHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerPushSubscription> findForUpdateByEndpointHash(String endpointHash);

    @Query(value = """
            SELECT subscription.*
            FROM customer_push_subscriptions subscription
            WHERE (
                subscription.expires_at IS NOT NULL
                AND subscription.expires_at <= :now
            ) OR (
                subscription.last_seen_at < :dormantCutoff
                AND NOT EXISTS (
                    SELECT 1
                    FROM customer_push_enrollments enrollment
                    WHERE enrollment.subscription_id = subscription.id
                )
            )
            ORDER BY COALESCE(subscription.expires_at, subscription.last_seen_at),
                     subscription.id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<CustomerPushSubscription> findExpiredOrDormantForCleanup(
            @Param("now") Instant now,
            @Param("dormantCutoff") Instant dormantCutoff,
            @Param("batchSize") int batchSize
    );
}
