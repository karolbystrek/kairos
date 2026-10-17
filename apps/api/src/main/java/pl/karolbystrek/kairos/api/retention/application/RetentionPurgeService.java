package pl.karolbystrek.kairos.api.retention.application;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.authentication.infrastructure.persistence.StaffIdentityStore;
import pl.karolbystrek.kairos.api.order.application.OrderCommandService;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerDiscoveryRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerOperation;
import pl.karolbystrek.kairos.api.retention.infrastructure.config.RetentionProperties;

import java.time.Clock;
import java.util.List;
import java.util.function.IntSupplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionPurgeService {

    private final MeterRegistry meters;
    private final WorkerDiscoveryRepository workerDiscoveryRepository;
    private final DatabaseAccessContext databaseAccessContext;
    private final StaffIdentityStore identities;
    private final CustomerOrderRepository orders;
    private final OrderCommandService orderCommands;
    private final RetentionProperties properties;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    public void purge() {
        var transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var cutoff = clock.instant().minus(properties.gracePeriod());
        var staleBefore = clock.instant().minus(properties.orderExpiry());
        var expired = purgeBatch("stale order", transactionTemplate, status -> {
            var id = workerDiscoveryRepository.nextStaleOrder(staleBefore);
            if (id == null || !databaseAccessContext.worker(WorkerOperation.ORDER_EXPIRY, id, null)) {
                return null;
            }
            return orderCommands.expire(orders.findById(id).orElseThrow(), clock.instant());
        });
        // Accounts go first: a location becomes purgeable once the accounts archived with it are gone.
        var accounts = purgeBatch("account", transactionTemplate, status -> {
            var account = workerDiscoveryRepository.nextPurgeableAccount(cutoff, List.of());
            if (account == null || !databaseAccessContext.worker(WorkerOperation.ACCOUNT_PURGE, account.id(), null)) {
                return null;
            }
            identities.deleteUser(account.providerSubject());
            workerDiscoveryRepository.purgeRetained(WorkerOperation.ACCOUNT_PURGE, account.id());
            return true;
        });
        var locations = purgeBatch("location", transactionTemplate, status -> {
            var id = workerDiscoveryRepository.nextPurgeableLocation(cutoff);
            if (id == null || !databaseAccessContext.worker(WorkerOperation.LOCATION_PURGE, id, null)) {
                return null;
            }
            workerDiscoveryRepository.purgeRetained(WorkerOperation.LOCATION_PURGE, id);
            return true;
        });
        var invitations = purgeBatch("invitation", transactionTemplate, status -> {
            var id = workerDiscoveryRepository.nextFinishedInvitation(cutoff);
            if (id == null || !databaseAccessContext.worker(WorkerOperation.INVITATION_PURGE, id, null)) {
                return null;
            }
            workerDiscoveryRepository.purgeRetained(WorkerOperation.INVITATION_PURGE, id);
            return true;
        });
        var registrations = purgeBatch("registration", transactionTemplate, status -> {
            var registration = workerDiscoveryRepository.nextAbandonedRegistration(cutoff, List.of());
            if (registration == null) {
                return null;
            }
            // A provisioned subject belongs to a real account whose verification lost only its cleanup.
            if (!registration.provisioned()) {
                identities.deleteUser(registration.subject());
            }
            workerDiscoveryRepository.purgeRegistration(registration.subject());
            return true;
        });
        var emails = purgeFinished("email deliveries", transactionTemplate,
                () -> workerDiscoveryRepository.purgeFinishedEmailDeliveries(cutoff, properties.batchSize()));
        // Deliveries go first: an outbox event is purgeable once no delivery references it.
        var webhooks = purgeFinished("webhook deliveries", transactionTemplate,
                () -> workerDiscoveryRepository.purgeFinishedWebhookDeliveries(cutoff, properties.batchSize()));
        var events = purgeFinished("order events", transactionTemplate,
                () -> workerDiscoveryRepository.purgeFinishedOrderEvents(cutoff, properties.batchSize()));
        if (expired + accounts + locations + invitations + registrations + emails + webhooks + events > 0) {
            log.info("Retention expired {} stale orders and removed {} accounts, {} locations, {} invitations, {} registrations, "
                    + "{} email deliveries, {} webhook deliveries and {} order events",
                    expired, accounts, locations, invitations, registrations, emails, webhooks, events);
        }
    }

    // Repeats until a batch comes back short or this kind's budget is spent; the first batch always runs.
    private int purgeFinished(String kind, TransactionTemplate transactionTemplate, IntSupplier purge) {
        var deadline = deadline();
        var total = 0;
        try {
            int purged;
            do {
                var result = transactionTemplate.execute(status -> purge.getAsInt());
                purged = result == null ? 0 : result;
                total += purged;
            } while (purged >= properties.batchSize() && System.nanoTime() - deadline < 0);
        } catch (RuntimeException exception) {
            meters.counter("kairos.worker.failures", "worker", "retention").increment();
            log.error("Retention purge of {} failed", kind, exception);
        }
        return total;
    }

    // The callback returns true when it purged an item and null (or false) when none is left.
    private int purgeBatch(String kind, TransactionTemplate transactionTemplate,
                           TransactionCallback<Boolean> purgeNext) {
        var deadline = deadline();
        var purged = 0;
        // The first item always runs; later ones only while the cycle budget lasts.
        while (purged == 0 || System.nanoTime() - deadline < 0) {
            Boolean result;
            try {
                result = transactionTemplate.execute(purgeNext);
            } catch (RuntimeException exception) {
                meters.counter("kairos.worker.failures", "worker", "retention").increment();
                // A failing item stops only its own batch; the other kinds still run this cycle.
                log.error("Retention purge of {} failed", kind, exception);
                break;
            }
            // An item that was not purged would be picked again, so it ends the batch like an empty queue.
            if (result == null || !result) {
                break;
            }
            purged++;
        }
        return purged;
    }

    // Each kind gets its own budget, so a backlog of one kind cannot starve the others.
    private long deadline() {
        return System.nanoTime() + properties.cycleBudget().toNanos();
    }
}
