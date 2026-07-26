package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookDeliverySigningVersion;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookDeliverySigningVersionId;

import java.util.List;
import java.util.UUID;

public interface WebhookDeliverySigningVersionRepository
        extends JpaRepository<WebhookDeliverySigningVersion, WebhookDeliverySigningVersionId> {

    List<WebhookDeliverySigningVersion> findAllByDeliveryId(UUID deliveryId);
}
