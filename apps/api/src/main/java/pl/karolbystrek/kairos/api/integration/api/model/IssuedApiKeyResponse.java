package pl.karolbystrek.kairos.api.integration.api.model;

import pl.karolbystrek.kairos.api.integration.application.model.IssuedApiKeyView;

public record IssuedApiKeyResponse(
        ApiKeyResponse apiKey,
        ApiKeyVersionResponse version,
        String secret
) {

    public static IssuedApiKeyResponse from(IssuedApiKeyView issued) {
        return new IssuedApiKeyResponse(
                ApiKeyResponse.from(issued.apiKey()),
                ApiKeyVersionResponse.from(issued.version()),
                issued.secret()
        );
    }
}
