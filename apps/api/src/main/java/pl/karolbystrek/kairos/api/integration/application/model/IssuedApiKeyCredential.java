package pl.karolbystrek.kairos.api.integration.application.model;

import lombok.NonNull;

import java.util.UUID;

public record IssuedApiKeyCredential(
        @NonNull UUID apiKeyId,
        @NonNull String value,
        @NonNull String hash
) {

    @Override
    public String toString() {
        return "IssuedApiKeyCredential[apiKeyId=" + apiKeyId + ", value=[REDACTED], hash=[REDACTED]]";
    }
}
