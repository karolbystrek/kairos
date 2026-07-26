package pl.karolbystrek.kairos.api.integration.webhook.api.model;

import pl.karolbystrek.kairos.api.integration.webhook.application.model.WebhookSigningSecretVersionView;

import java.time.Instant;
import java.util.UUID;

public record WebhookSigningSecretVersionResponse(
        UUID id,
        Instant issuedAt,
        Instant validUntil,
        Instant retiredAt
) {

    public static WebhookSigningSecretVersionResponse from(WebhookSigningSecretVersionView view) {
        return new WebhookSigningSecretVersionResponse(
                view.id(),
                view.issuedAt(),
                view.validUntil(),
                view.retiredAt()
        );
    }
}
