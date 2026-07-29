package pl.karolbystrek.kairos.api.integration.webhook.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.order.domain.OrderEventType;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscription;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscriptionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record WebhookSubscriptionView(
        @NonNull UUID id,
        @NonNull UUID integrationId,
        @NonNull String name,
        @NonNull String destinationUrl,
        @NonNull WebhookSubscriptionStatus status,
        @NonNull Set<UUID> locationIds,
        @NonNull Set<OrderEventType> eventTypes,
        @NonNull List<WebhookSigningSecretVersionView> signingSecretVersions,
        @NonNull Instant createdAt,
        @NonNull Instant updatedAt
) {

    public static WebhookSubscriptionView from(
            WebhookSubscription subscription,
            Set<UUID> locationIds,
            Set<OrderEventType> eventTypes,
            List<WebhookSigningSecretVersionView> signingSecretVersions
    ) {
        return new WebhookSubscriptionView(
                subscription.getId(),
                subscription.getIntegrationId(),
                subscription.getName(),
                subscription.getDestinationUrl(),
                subscription.getStatus(),
                Set.copyOf(locationIds),
                Set.copyOf(eventTypes),
                List.copyOf(signingSecretVersions),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}
