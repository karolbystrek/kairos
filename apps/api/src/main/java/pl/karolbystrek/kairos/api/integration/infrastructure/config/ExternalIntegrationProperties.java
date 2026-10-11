package pl.karolbystrek.kairos.api.integration.infrastructure.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("kairos.external-integrations")
public record ExternalIntegrationProperties(Duration apiKeyUsageRefresh) {

    private static final Duration DEFAULT_API_KEY_USAGE_REFRESH = Duration.ofHours(1);

    public ExternalIntegrationProperties {
        apiKeyUsageRefresh = apiKeyUsageRefresh == null
                ? DEFAULT_API_KEY_USAGE_REFRESH
                : apiKeyUsageRefresh;
    }

    @AssertTrue(message = "API Key usage refresh must be positive")
    public boolean isApiKeyUsageRefreshPositive() {
        return apiKeyUsageRefresh != null && apiKeyUsageRefresh.isPositive();
    }
}
