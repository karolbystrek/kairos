package pl.karolbystrek.kairos.api.retention.infrastructure.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("kairos.retention")
public record RetentionProperties(
        @NotNull Duration gracePeriod,
        @Min(1) int batchSize,
        @NotNull Duration orderExpiry
) {

    @AssertTrue(message = "Retention grace period must be positive")
    public boolean isGracePeriodPositive() {
        return gracePeriod == null || gracePeriod.isPositive();
    }
}
