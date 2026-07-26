package pl.karolbystrek.kairos.api.integration.infrastructure.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("kairos.external-integrations")
public record ExternalIntegrationProperties(Duration apiKeyRotationGrace) {

    private static final Duration DEFAULT_API_KEY_ROTATION_GRACE = Duration.ofHours(24);

    public ExternalIntegrationProperties {
        apiKeyRotationGrace = apiKeyRotationGrace == null
                ? DEFAULT_API_KEY_ROTATION_GRACE
                : apiKeyRotationGrace;
    }

    @AssertTrue(message = "API Key rotation grace must be positive")
    public boolean isApiKeyRotationGracePositive() {
        return apiKeyRotationGrace != null && apiKeyRotationGrace.isPositive();
    }
}
