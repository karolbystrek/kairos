package pl.karolbystrek.kairos.api.notification.infrastructure.config;

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
@ConfigurationProperties("kairos.customer-notifications")
public record CustomerNotificationProperties(
        @Valid @NotNull Vapid vapid,
        @Valid @NotNull Subscription subscription,
        @Valid @NotNull Delivery delivery,
        @Valid @NotNull Worker worker,
        @NotNull DestinationPolicy destinationPolicy
) {

    @AssertTrue(message = "Customer Push claim lease must exceed its total HTTP timeout")
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

    public record Vapid(
            @NotBlank String publicKeyLocation,
            @NotBlank String privateKeyLocation,
            @NotBlank String subject
    ) {
    }

    public record Subscription(
            @NotBlank String encryptionKeyLocation,
            @Min(1) @Max(100) int maximumEnrollmentsPerOrder,
            @NotNull Duration dormantRetention
    ) {

        @AssertTrue(message = "Customer Push dormant retention must be positive")
        public boolean isDormantRetentionPositive() {
            return dormantRetention == null || dormantRetention.isPositive();
        }
    }

    public record Delivery(
            @NotNull Duration freshnessWindow,
            @Min(1) @Max(32) int maximumAttempts,
            @NotNull Duration initialRetryDelay,
            @NotNull Duration maximumRetryDelay,
            @NotNull Duration totalTimeout,
            @NotNull Duration successfulRetention,
            @NotNull Duration failedRetention,
            @Min(512) @Max(4096) int maximumPayloadBytes
    ) {

        @AssertTrue(message = "Customer Push delivery durations must be positive")
        public boolean areDurationsPositive() {
            return (freshnessWindow == null || freshnessWindow.isPositive())
                    && (initialRetryDelay == null || initialRetryDelay.isPositive())
                    && (maximumRetryDelay == null || maximumRetryDelay.isPositive())
                    && (totalTimeout == null || totalTimeout.isPositive())
                    && (successfulRetention == null || successfulRetention.isPositive())
                    && (failedRetention == null || failedRetention.isPositive());
        }
    }

    public record Worker(
            @Min(1) @Max(1000) int batchSize,
            @NotNull Duration claimLease
    ) {

        @AssertTrue(message = "Customer Push worker claim lease must be positive")
        public boolean isClaimLeasePositive() {
            return claimLease == null || claimLease.isPositive();
        }
    }
}
