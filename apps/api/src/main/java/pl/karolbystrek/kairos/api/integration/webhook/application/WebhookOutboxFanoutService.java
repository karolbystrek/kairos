package pl.karolbystrek.kairos.api.integration.webhook.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookDelivery;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookDeliverySigningVersion;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config.WebhookProperties;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookDeliveryRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookDeliverySigningVersionRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookSigningSecretVersionRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookSubscriptionRepository;
import pl.karolbystrek.kairos.api.order.domain.OrderOutboxEvent;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.OrderOutboxEventRepository;

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

    @Transactional
    public int fanOutAvailable() {
        var events = outboxRepository.findAvailableForWebhookFanout(properties.worker().batchSize());
        for (var event : events) {
            fanOut(event);
        }
        return events.size();
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
