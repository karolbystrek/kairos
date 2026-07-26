package pl.karolbystrek.kairos.api.integration.webhook.api.model;

import pl.karolbystrek.kairos.api.integration.webhook.application.model.WebhookSubscriptionView;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscriptionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record WebhookSubscriptionResponse(
        UUID id,
        UUID integrationId,
        String name,
        String destinationUrl,
        WebhookSubscriptionStatus status,
        Set<UUID> locationIds,
        Set<String> eventTypes,
        List<WebhookSigningSecretVersionResponse> signingSecretVersions,
        Instant createdAt,
        Instant updatedAt
) {

    public static WebhookSubscriptionResponse from(WebhookSubscriptionView view) {
        return new WebhookSubscriptionResponse(
                view.id(),
                view.integrationId(),
                view.name(),
                view.destinationUrl(),
                view.status(),
                view.locationIds(),
                view.eventTypes().stream()
                        .map(eventType -> eventType.cloudEventType())
                        .collect(Collectors.toUnmodifiableSet()),
                view.signingSecretVersions().stream()
                        .map(WebhookSigningSecretVersionResponse::from)
                        .toList(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}
