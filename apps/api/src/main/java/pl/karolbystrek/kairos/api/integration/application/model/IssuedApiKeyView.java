package pl.karolbystrek.kairos.api.integration.application.model;

import lombok.NonNull;

public record IssuedApiKeyView(
        @NonNull ApiKeyView apiKey,
        @NonNull ApiKeyVersionView version,
        @NonNull String secret
) {
}
