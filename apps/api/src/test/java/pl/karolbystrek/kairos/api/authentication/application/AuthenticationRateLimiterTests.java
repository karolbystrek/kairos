package pl.karolbystrek.kairos.api.authentication.application;

import org.junit.jupiter.api.Test;
import pl.karolbystrek.kairos.api.authentication.application.exception.RateLimitExceededException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticationRateLimiterTests {

    private final AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(
        Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC),
        2,
        Duration.ofMinutes(5),
        2,
        Duration.ofMinutes(1),
        2,
        Duration.ofHours(1)
    );

    @Test
    void limitsRepeatedLoginAttemptsForTheSameClientAndAccount() {
        limiter.checkLogin("192.0.2.1", "panel-device");
        limiter.checkLogin("192.0.2.1", "panel-device");

        assertThatThrownBy(() -> limiter.checkLogin("192.0.2.1", "panel-device"))
            .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void clearsAccountAndPairLimitsAfterSuccessfulLogin() {
        limiter.checkLogin("192.0.2.2", "manager");
        limiter.checkLogin("192.0.2.2", "manager");
        limiter.loginSucceeded("192.0.2.2", "manager");

        limiter.checkLogin("192.0.2.2", "manager");
        limiter.checkLogin("192.0.2.2", "manager");
    }

    @Test
    void limitsRefreshAttemptsByCredential() {
        limiter.checkRefresh("192.0.2.3", "refresh-credential");
        limiter.checkRefresh("192.0.2.3", "refresh-credential");

        assertThatThrownBy(() -> limiter.checkRefresh("192.0.2.3", "refresh-credential"))
            .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void limitsTenantRegistrationAttemptsByClient() {
        limiter.checkTenantRegistration("192.0.2.5");
        limiter.checkTenantRegistration("192.0.2.5");

        assertThatThrownBy(() -> limiter.checkTenantRegistration("192.0.2.5"))
            .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void allowsIndependentCredentialsBehindOneSharedClientAddress() {
        for (var index = 0; index < 20; index++) {
            limiter.checkRefresh("192.0.2.4", "shared-address-credential-" + index);
        }

        assertThatThrownBy(() -> limiter.checkRefresh("192.0.2.4", "one-client-attempt-too-many"))
            .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void failsClosedWhenTheTrackedWindowCapacityIsExhausted() {
        for (var index = 0; index < 5_000; index++) {
            limiter.checkRefresh("capacity-client-" + index, "capacity-credential-" + index);
        }

        assertThatThrownBy(() -> limiter.checkRefresh("one-client-too-many", "one-credential-too-many"))
            .isInstanceOf(RateLimitExceededException.class);
    }
}
