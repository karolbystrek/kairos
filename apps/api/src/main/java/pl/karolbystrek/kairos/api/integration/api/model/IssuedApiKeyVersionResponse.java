package pl.karolbystrek.kairos.api.integration.api.model;

import pl.karolbystrek.kairos.api.integration.application.model.IssuedApiKeyVersionView;

public record IssuedApiKeyVersionResponse(
        ApiKeyVersionResponse version,
        String secret
) {

    public static IssuedApiKeyVersionResponse from(IssuedApiKeyVersionView issued) {
        return new IssuedApiKeyVersionResponse(
                ApiKeyVersionResponse.from(issued.version()),
                issued.secret()
        );
    }
}
