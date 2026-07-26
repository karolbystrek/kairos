package pl.karolbystrek.kairos.api.integration.api.model;

import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyVersionView;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyVersionResponse(
        UUID id,
        UUID apiKeyId,
        Instant issuedAt,
        Instant validUntil,
        Instant retiredAt
) {

    public static ApiKeyVersionResponse from(ApiKeyVersionView version) {
        return new ApiKeyVersionResponse(
                version.id(),
                version.apiKeyId(),
                version.issuedAt(),
                version.validUntil(),
                version.retiredAt()
        );
    }
}
