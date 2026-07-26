package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscriptionEventSelection;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscriptionEventSelectionId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WebhookSubscriptionEventSelectionRepository
        extends JpaRepository<WebhookSubscriptionEventSelection, WebhookSubscriptionEventSelectionId> {

    List<WebhookSubscriptionEventSelection> findAllBySubscriptionId(UUID subscriptionId);

    List<WebhookSubscriptionEventSelection> findAllBySubscriptionIdIn(
            Collection<UUID> subscriptionIds
    );

    long deleteAllBySubscriptionId(UUID subscriptionId);
}
