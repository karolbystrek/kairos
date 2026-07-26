package pl.karolbystrek.kairos.api.integration.application.model;

import lombok.NonNull;

import java.util.UUID;

public record IssuedApiKeyCredential(
        @NonNull UUID versionId,
        @NonNull String value,
        @NonNull String hash
) {
}
