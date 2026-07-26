package pl.karolbystrek.kairos.api.integration.webhook.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.integration.webhook.application.model.ClaimedWebhookDelivery;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookDeliverySigningVersion;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config.WebhookProperties;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookDeliveryRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookDeliverySigningVersionRepository;

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

    @Transactional
    public List<ClaimedWebhookDelivery> claimAvailable() {
        var now = clock.instant();
        return deliveryRepository.findAvailableForClaim(now, properties.worker().batchSize())
                .stream()
                .map(delivery -> {
                    var claimToken = UUID.randomUUID();
                    delivery.claim(claimToken, now, properties.worker().claimLease());
                    var signingVersionIds = deliverySigningRepository
                            .findAllByDeliveryId(delivery.getId())
                            .stream()
                            .map(WebhookDeliverySigningVersion::getSigningSecretVersionId)
                            .toList();
                    if (signingVersionIds.isEmpty()) {
                        throw new IllegalStateException(
                                "Webhook delivery has no captured signing-secret version"
                        );
                    }
                    return new ClaimedWebhookDelivery(
                            delivery.getId(),
                            claimToken,
                            delivery.getSubscriptionId(),
                            delivery.getDestinationUrl(),
                            delivery.getPayload(),
                            signingVersionIds
                    );
                })
                .toList();
    }
}
