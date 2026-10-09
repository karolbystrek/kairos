package pl.karolbystrek.kairos.api.persistence.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class WorkerDiscoveryRepository {

    private final EntityManager entityManager;

    // Discovery locks must remain on the same Hibernate transaction as scope binding and entity work.
    public UUID nextPushFanout() {
        return (UUID) entityManager.createNativeQuery("SELECT public.next_push_fanout()", UUID.class)
                .getSingleResult();
    }

    public UUID nextWebhookFanout() {
        return (UUID) entityManager.createNativeQuery("SELECT public.next_webhook_fanout()", UUID.class)
                .getSingleResult();
    }

    public UUID nextPushDelivery(Instant now) {
        return (UUID) entityManager.createNativeQuery("SELECT public.next_push_delivery(:now)", UUID.class)
                .setParameter("now", now)
                .getSingleResult();
    }

    public UUID nextWebhookDelivery(Instant now) {
        return (UUID) entityManager.createNativeQuery("SELECT public.next_webhook_delivery(:now)", UUID.class)
                .setParameter("now", now)
                .getSingleResult();
    }

    public UUID nextExpiredPushSubscription(Instant now) {
        return (UUID) entityManager.createNativeQuery("SELECT public.next_expired_push_subscription(:now)", UUID.class)
                .setParameter("now", now)
                .getSingleResult();
    }

    public UUID nextDormantPushSubscription(Instant cutoff) {
        return (UUID) entityManager.createNativeQuery("SELECT public.next_dormant_push_subscription(:cutoff)", UUID.class)
                .setParameter("cutoff", cutoff)
                .getSingleResult();
    }

    public UUID nextTerminalPushDelivery(Instant successfulCutoff, Instant failedCutoff) {
        return (UUID) entityManager.createNativeQuery("""
                SELECT public.next_terminal_push_delivery(:successfulCutoff, :failedCutoff)
                """, UUID.class)
                .setParameter("successfulCutoff", successfulCutoff)
                .setParameter("failedCutoff", failedCutoff)
                .getSingleResult();
    }
}
