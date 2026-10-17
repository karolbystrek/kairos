package pl.karolbystrek.kairos.api.persistence.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class WorkerDiscoveryRepository {

    private final EntityManager entityManager;

    // Discovery locks must remain on the same Hibernate transaction as scope binding and entity work.
    public UUID nextOrderMeasurement() {
        return (UUID) entityManager.createNativeQuery("SELECT public.next_order_measurement()", UUID.class)
                .getSingleResult();
    }

    public UUID nextPushFanout() {
        return (UUID) entityManager.createNativeQuery("SELECT public.next_push_fanout()", UUID.class)
                .getSingleResult();
    }

    // Counts a failed fan-out so the event is skipped after repeated failures instead of blocking the queue.
    public void recordFanoutFailure(UUID eventId, WorkerOperation operation, Instant now, String error) {
        entityManager.createNativeQuery(
                        "SELECT public.record_outbox_fanout_failure(:event, :kind, :now, :error)", Boolean.class)
                .setParameter("event", eventId)
                .setParameter("kind", operation.name())
                .setParameter("now", now)
                .setParameter("error", error)
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

    public PurgeableAccount nextPurgeableAccount(Instant cutoff, Collection<UUID> skipped) {
        var rows = entityManager.createNativeQuery("SELECT * FROM public.next_purgeable_account(:cutoff, :skipped)", Object[].class)
                .setParameter("cutoff", cutoff)
                .setParameter("skipped", skipped.stream().map(UUID::toString).collect(Collectors.joining(",")))
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        var row = (Object[]) rows.getFirst();
        return new PurgeableAccount((UUID) row[0], (String) row[1]);
    }

    public UUID nextPurgeableLocation(Instant cutoff) {
        return (UUID) entityManager.createNativeQuery("SELECT public.next_purgeable_location(:cutoff)", UUID.class)
                .setParameter("cutoff", cutoff)
                .getSingleResult();
    }

    public UUID nextFinishedInvitation(Instant cutoff) {
        return (UUID) entityManager.createNativeQuery("SELECT public.next_finished_invitation(:cutoff)", UUID.class)
                .setParameter("cutoff", cutoff)
                .getSingleResult();
    }

    // Requires the identity discovered and bound as a worker in this transaction.
    public void purgeRetained(WorkerOperation operation, UUID id) {
        entityManager.createNativeQuery("SELECT public.purge_retained(:operation, :id)", Boolean.class)
                .setParameter("operation", operation.name())
                .setParameter("id", id)
                .getSingleResult();
    }

    public record PurgeableAccount(UUID id, String providerSubject) {
    }

    public AbandonedRegistration nextAbandonedRegistration(Instant cutoff, Collection<String> skipped) {
        var rows = entityManager.createNativeQuery("SELECT * FROM public.next_abandoned_registration(:cutoff, :skipped)", Object[].class)
                .setParameter("cutoff", cutoff)
                .setParameter("skipped", String.join(",", skipped))
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        var row = (Object[]) rows.getFirst();
        return new AbandonedRegistration((String) row[0], (Boolean) row[1]);
    }

    // Requires the registration discovered in this transaction.
    public void purgeRegistration(String subject) {
        entityManager.createNativeQuery("SELECT public.purge_registration(:subject)", Boolean.class)
                .setParameter("subject", subject)
                .getSingleResult();
    }

    public UUID nextStaleOrder(Instant cutoff) {
        return (UUID) entityManager.createNativeQuery("SELECT public.next_stale_order(:cutoff)", UUID.class)
                .setParameter("cutoff", cutoff)
                .getSingleResult();
    }

    public int purgeFinishedWebhookDeliveries(Instant cutoff, int batchSize) {
        return (Integer) entityManager.createNativeQuery("SELECT public.purge_finished_webhook_deliveries(:cutoff, :batch)", Integer.class)
                .setParameter("cutoff", cutoff)
                .setParameter("batch", batchSize)
                .getSingleResult();
    }

    public int purgeFinishedOrderEvents(Instant cutoff, int batchSize) {
        return (Integer) entityManager.createNativeQuery("SELECT public.purge_finished_order_events(:cutoff, :batch)", Integer.class)
                .setParameter("cutoff", cutoff)
                .setParameter("batch", batchSize)
                .getSingleResult();
    }

    public int purgeFinishedEmailDeliveries(Instant cutoff, int batchSize) {
        return (Integer) entityManager.createNativeQuery("SELECT public.purge_finished_email_deliveries(:cutoff, :batch)", Integer.class)
                .setParameter("cutoff", cutoff)
                .setParameter("batch", batchSize)
                .getSingleResult();
    }

    public record AbandonedRegistration(String subject, boolean provisioned) {
    }
}
