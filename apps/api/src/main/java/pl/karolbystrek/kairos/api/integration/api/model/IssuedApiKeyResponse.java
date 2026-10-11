package pl.karolbystrek.kairos.api.integration.api.model;

import pl.karolbystrek.kairos.api.integration.application.model.IssuedApiKeyView;

public record IssuedApiKeyResponse(
        ApiKeyResponse apiKey,
        String secret
) {

    public static IssuedApiKeyResponse from(IssuedApiKeyView issued) {
        return new IssuedApiKeyResponse(
                ApiKeyResponse.from(issued.apiKey()),
                issued.secret()
        );
    }

    @Override
    public String toString() {
        return "IssuedApiKeyResponse[apiKey=" + apiKey + ", secret=[REDACTED]]";
    }
}
