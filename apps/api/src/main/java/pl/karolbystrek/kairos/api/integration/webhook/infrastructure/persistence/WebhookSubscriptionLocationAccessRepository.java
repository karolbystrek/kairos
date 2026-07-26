package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscriptionLocationAccess;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscriptionLocationAccessId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WebhookSubscriptionLocationAccessRepository
        extends JpaRepository<WebhookSubscriptionLocationAccess, WebhookSubscriptionLocationAccessId> {

    List<WebhookSubscriptionLocationAccess> findAllBySubscriptionId(UUID subscriptionId);

    List<WebhookSubscriptionLocationAccess> findAllBySubscriptionIdIn(
            Collection<UUID> subscriptionIds
    );

    long deleteAllBySubscriptionId(UUID subscriptionId);
}
