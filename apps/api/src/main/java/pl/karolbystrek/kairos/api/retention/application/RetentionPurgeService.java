package pl.karolbystrek.kairos.api.retention.application;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerDiscoveryRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerOperation;
import pl.karolbystrek.kairos.api.retention.infrastructure.config.RetentionProperties;

import java.time.Clock;
import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionPurgeService {

    private final MeterRegistry meters;
    private final WorkerDiscoveryRepository workerDiscoveryRepository;
    private final DatabaseAccessContext databaseAccessContext;
    private final ZitadelClient identityProvider;
    private final RetentionProperties properties;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    public void purge() {
        var transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var cutoff = clock.instant().minus(properties.gracePeriod());
        var deferredAccounts = new HashSet<UUID>();
        // Accounts go first: a location becomes purgeable once the accounts archived with it are gone.
        var accounts = purgeBatch("account", transactionTemplate, status -> {
            var account = workerDiscoveryRepository.nextPurgeableAccount(cutoff, deferredAccounts);
            if (account == null || !databaseAccessContext.worker(WorkerOperation.ACCOUNT_PURGE, account.id(), null)) {
                return null;
            }
            try {
                // The discovery lock is held across this call so the identity is deleted at most once concurrently.
                identityProvider.deleteUser(account.providerSubject());
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode().value() != 404) {
                    meters.counter("kairos.worker.failures", "worker", "retention").increment();
                    log.warn("Deferred purge of account {}: identity provider returned {}",
                            account.id(), exception.getStatusCode().value());
                    deferredAccounts.add(account.id());
                    status.setRollbackOnly();
                    return false;
                }
            }
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
        var deferredRegistrations = new HashSet<String>();
        var registrations = purgeBatch("registration", transactionTemplate, status -> {
            var registration = workerDiscoveryRepository.nextAbandonedRegistration(cutoff, deferredRegistrations);
            if (registration == null) {
                return null;
            }
            // A provisioned subject belongs to a real account whose verification lost only its cleanup.
            if (!registration.provisioned()) {
                try {
                    identityProvider.deleteUser(registration.subject());
                } catch (ResponseStatusException exception) {
                    if (exception.getStatusCode().value() != 404) {
                        meters.counter("kairos.worker.failures", "worker", "retention").increment();
                        log.warn("Deferred purge of abandoned registration: identity provider returned {}",
                                exception.getStatusCode().value());
                        deferredRegistrations.add(registration.subject());
                        status.setRollbackOnly();
                        return false;
                    }
                }
            }
            workerDiscoveryRepository.purgeRegistration(registration.subject());
            return true;
        });
        Integer emails;
        try {
            emails = transactionTemplate.execute(status ->
                    workerDiscoveryRepository.purgeFinishedEmailDeliveries(cutoff, properties.batchSize()));
        } catch (RuntimeException exception) {
            meters.counter("kairos.worker.failures", "worker", "retention").increment();
            log.error("Retention purge of email deliveries failed", exception);
            emails = 0;
        }
        if (accounts + locations + invitations + registrations + emails > 0) {
            log.info("Retention purge removed {} accounts, {} locations, {} invitations, {} registrations and {} email deliveries",
                    accounts, locations, invitations, registrations, emails);
        }
    }

    // The callback returns true when it purged an item, false when it deferred one and null when none is left.
    private int purgeBatch(String kind, TransactionTemplate transactionTemplate, TransactionCallback<Boolean> purgeNext) {
        var purged = 0;
        for (var attempt = 0; attempt < properties.batchSize(); attempt++) {
            Boolean result;
            try {
                result = transactionTemplate.execute(purgeNext);
            } catch (RuntimeException exception) {
                meters.counter("kairos.worker.failures", "worker", "retention").increment();
                // A failing item stops only its own batch; the other kinds still run this cycle.
                log.error("Retention purge of {} failed", kind, exception);
                break;
            }
            if (result == null) {
                break;
            }
            if (result) {
                purged++;
            }
        }
        return purged;
    }
}
