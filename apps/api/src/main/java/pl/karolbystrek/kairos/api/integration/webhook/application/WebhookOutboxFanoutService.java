package pl.karolbystrek.kairos.api.integration.webhook.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookDelivery;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookDeliverySigningVersion;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config.WebhookProperties;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookDeliveryRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookDeliverySigningVersionRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookSigningSecretVersionRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookSubscriptionRepository;
import pl.karolbystrek.kairos.api.order.domain.OrderOutboxEvent;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.OrderOutboxEventRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerOperation;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerDiscoveryRepository;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class WebhookOutboxFanoutService {

    private final OrderOutboxEventRepository outboxRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookSigningSecretVersionRepository signingSecretRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDeliverySigningVersionRepository deliverySigningRepository;
    private final WebhookProperties properties;
    private final Clock clock;
    private final WorkerDiscoveryRepository workerDiscoveryRepository;
    private final PlatformTransactionManager transactionManager;
    private final DatabaseAccessContext databaseAccess;

    public int fanOutAvailable() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var processed = 0;
        for (; processed < properties.worker().batchSize(); processed++) {
            var found = transaction.execute(status -> {
                var eventId = workerDiscoveryRepository.nextWebhookFanout();
                if (eventId == null || !databaseAccess.worker(WorkerOperation.WEBHOOK_FANOUT, eventId, null)) {
                    return false;
                }
                fanOut(outboxRepository.findById(eventId).orElseThrow());
                return true;
            });
            if (!Boolean.TRUE.equals(found)) break;
        }
        return processed;
    }

    private void fanOut(OrderOutboxEvent event) {
        var subscriptions = subscriptionRepository.findMatchingForFanout(
                event.getTenantId(),
                event.getLocationId(),
                event.getEventType().name(),
                event.getOccurredAt()
        );
        var now = clock.instant();
        for (var subscription : subscriptions) {
            if (deliveryRepository.existsByOutboxEventIdAndSubscriptionId(
                    event.getId(),
                    subscription.getId()
            )) {
                continue;
            }
            var signingVersions = signingSecretRepository.findActiveForDelivery(
                    subscription.getId(),
                    now
            );
            if (signingVersions.isEmpty() || signingVersions.size() > 2) {
                throw new IllegalStateException(
                        "An enabled webhook subscription must have one or two active signing versions"
                );
            }
            var delivery = deliveryRepository.saveAndFlush(WebhookDelivery.create(
                    event.getId(),
                    subscription.getId(),
                    subscription.getDestinationUrl(),
                    event.getWebhookPayload(),
                    now
            ));
            deliverySigningRepository.saveAll(signingVersions.stream()
                    .map(version -> WebhookDeliverySigningVersion.create(
                            delivery.getId(),
                            version.getId()
                    ))
                    .toList());
        }
        event.completeWebhookFanout(now);
    }
}
