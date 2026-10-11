package pl.karolbystrek.kairos.api.integration.application.model;

import lombok.NonNull;

public record IssuedApiKeyView(
        @NonNull ApiKeyView apiKey,
        @NonNull String secret
) {

    @Override
    public String toString() {
        return "IssuedApiKeyView[apiKey=" + apiKey + ", secret=[REDACTED]]";
    }
}
