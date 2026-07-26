package pl.karolbystrek.kairos.api.integration.application.model;

import lombok.NonNull;
import pl.karolbystrek.kairos.api.integration.domain.ApiKeyVersion;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyVersionView(
        @NonNull UUID id,
        @NonNull UUID apiKeyId,
        @NonNull Instant issuedAt,
        Instant validUntil,
        Instant retiredAt
) {

    public static ApiKeyVersionView from(ApiKeyVersion version) {
        return new ApiKeyVersionView(
                version.getId(),
                version.getApiKeyId(),
                version.getIssuedAt(),
                version.getValidUntil(),
                version.getRetiredAt()
        );
    }
}
