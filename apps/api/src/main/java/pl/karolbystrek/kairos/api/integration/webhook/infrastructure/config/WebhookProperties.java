package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("kairos.webhooks")
public record WebhookProperties(
        @Valid @NotNull Signing signing,
        @Valid @NotNull Delivery delivery,
        @Valid @NotNull Worker worker,
        @NotNull DestinationPolicy destinationPolicy
) {

    @AssertTrue(message = "Webhook worker claim lease must exceed the delivery timeout")
    public boolean isClaimLeaseLongEnough() {
        return delivery == null
                || delivery.totalTimeout() == null
                || worker == null
                || worker.claimLease() == null
                || worker.claimLease().compareTo(delivery.totalTimeout()) > 0;
    }

    public enum DestinationPolicy {
        PUBLIC_HTTPS,
        LOCAL_DEVELOPMENT
    }

    public record Signing(
            @NotBlank String encryptionKeyLocation,
            @NotNull Duration rotationOverlap
    ) {
        @AssertTrue(message = "Webhook signing-secret rotation overlap must be positive")
        public boolean isRotationOverlapPositive() {
            return rotationOverlap == null || rotationOverlap.isPositive();
        }
    }

    public record Delivery(
            @NotNull Duration totalTimeout,
            @Min(1024) @Max(65536) int maximumResponseBodyBytes
    ) {
        @AssertTrue(message = "Webhook delivery timeout must be positive")
        public boolean isTotalTimeoutPositive() {
            return totalTimeout == null || totalTimeout.isPositive();
        }
    }

    public record Worker(
            @Min(1) @Max(1000) int batchSize,
            @NotNull Duration claimLease
    ) {
        @AssertTrue(message = "Webhook worker claim lease must be positive")
        public boolean isClaimLeasePositive() {
            return claimLease == null || claimLease.isPositive();
        }
    }
}
