package pl.karolbystrek.kairos.api.integration.application.model;

import lombok.NonNull;

public record IssuedApiKeyVersionView(
        @NonNull ApiKeyVersionView version,
        @NonNull String secret
) {
}
