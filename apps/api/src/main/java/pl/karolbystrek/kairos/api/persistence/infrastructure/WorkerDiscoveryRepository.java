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
}
