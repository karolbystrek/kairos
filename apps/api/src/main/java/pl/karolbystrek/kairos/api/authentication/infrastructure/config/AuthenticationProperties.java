package pl.karolbystrek.kairos.api.authentication.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("kairos.authentication")
public record AuthenticationProperties(
    @Valid @NotNull Jwt jwt,
    @Valid @NotNull Refresh refresh,
    @Valid @NotNull Password password
) {

    @AssertTrue(message = "Refresh idle lifetime must not exceed its absolute lifetime")
    public boolean isRefreshLifetimeOrderValid() {
        return refresh == null
            || refresh.idleLifetime() == null
            || refresh.absoluteLifetime() == null
            || refresh.idleLifetime().compareTo(refresh.absoluteLifetime()) <= 0;
    }

    public record Jwt(
        @NotBlank
        @Pattern(regexp = "https://\\S+", message = "JWT issuer must be an HTTPS URL")
        String issuer,
        @NotBlank String audience,
        @NotNull Duration accessLifetime,
        @NotBlank String publicKeyLocation,
        @NotBlank String privateKeyLocation
    ) {
        @AssertTrue(message = "Access-token lifetime must be positive")
        public boolean isAccessLifetimePositive() {
            return accessLifetime == null || accessLifetime.isPositive();
        }
    }

    public record Refresh(
        @NotNull Duration idleLifetime,
        @NotNull Duration absoluteLifetime
    ) {
        @AssertTrue(message = "Refresh lifetimes must be positive")
        public boolean areLifetimesPositive() {
            return idleLifetime == null
            || absoluteLifetime == null
            || (idleLifetime.isPositive() && absoluteLifetime.isPositive());
        }
    }

    public record Password(
        @Min(4) @Max(31) int bcryptStrength
    ) {
    }
}
