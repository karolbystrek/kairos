package pl.karolbystrek.kairos.api.notification.infrastructure.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("kairos.review-invitations")
public record ReviewInvitationProperties(@DefaultValue("30m") @NotNull Duration delay) {
    @AssertTrue(message = "Review invitation delay must be positive")
    public boolean isDelayPositive() {
        return delay == null || delay.isPositive();
    }
}
