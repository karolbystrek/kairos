package pl.karolbystrek.kairos.api.integration.webhook.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.integration.webhook.application.model.ClaimedWebhookDelivery;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookDeliverySigningVersion;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSigningSecretVersion;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config.WebhookProperties;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookDeliveryRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookDeliverySigningVersionRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookSigningSecretVersionRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.security.SigningSecretCipher;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerOperation;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerDiscoveryRepository;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebhookDeliveryClaimService {

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDeliverySigningVersionRepository deliverySigningRepository;
    private final WebhookProperties properties;
    private final Clock clock;
    private final WorkerDiscoveryRepository workerDiscoveryRepository;
    private final PlatformTransactionManager transactionManager;
    private final DatabaseAccessContext databaseAccess;
    private final WebhookSigningSecretVersionRepository signingSecretRepository;
    private final SigningSecretCipher signingSecretCipher;

    public List<ClaimedWebhookDelivery> claimAvailable() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var claimed = new java.util.ArrayList<ClaimedWebhookDelivery>();
        for (var index = 0; index < properties.worker().batchSize(); index++) {
            var delivery = transaction.execute(status -> claimNext());
            if (delivery == null) break;
            claimed.add(delivery);
        }
        return List.copyOf(claimed);
    }

    private ClaimedWebhookDelivery claimNext() {
        var now = clock.instant();
        var id = workerDiscoveryRepository.nextWebhookDelivery(now);
        if (id == null || !databaseAccess.worker(WorkerOperation.WEBHOOK_CLAIM, id, null)) return null;
        var delivery = deliveryRepository.findById(id).orElseThrow();
        var claimToken = UUID.randomUUID();
        delivery.claim(claimToken, now, properties.worker().claimLease());
        List<byte[]> signingSecrets;
        try {
            var ids = deliverySigningRepository.findAllByDeliveryId(id).stream()
                    .map(WebhookDeliverySigningVersion::getSigningSecretVersionId).toList();
            var versions = signingSecretRepository.findAllById(ids).stream()
                    .sorted(java.util.Comparator.comparing(WebhookSigningSecretVersion::getIssuedAt).reversed()).toList();
            if (ids.isEmpty() || versions.size() != ids.size()) {
                throw new IllegalStateException("Webhook delivery references missing signing material");
            }
            signingSecrets = versions.stream().map(version -> signingSecretCipher.decrypt(
                    version.getEncryptedSecret(), version.getEncryptionNonce(), delivery.getSubscriptionId(), version.getId())).toList();
        } catch (RuntimeException exception) {
            signingSecrets = List.of();
        }
        return new ClaimedWebhookDelivery(id, claimToken, delivery.getSubscriptionId(),
                delivery.getDestinationUrl(), delivery.getPayload(), signingSecrets);
    }
}
