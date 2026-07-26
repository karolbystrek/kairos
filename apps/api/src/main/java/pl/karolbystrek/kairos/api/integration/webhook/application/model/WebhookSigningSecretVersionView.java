package pl.karolbystrek.kairos.api.integration.webhook.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSigningSecretVersion;

import java.time.Instant;
import java.util.UUID;

public record WebhookSigningSecretVersionView(
        @NonNull UUID id,
        @NonNull Instant issuedAt,
        Instant validUntil,
        Instant retiredAt
) {

    public static WebhookSigningSecretVersionView from(WebhookSigningSecretVersion version) {
        return new WebhookSigningSecretVersionView(
                version.getId(),
                version.getIssuedAt(),
                version.getValidUntil(),
                version.getRetiredAt()
        );
    }
}
