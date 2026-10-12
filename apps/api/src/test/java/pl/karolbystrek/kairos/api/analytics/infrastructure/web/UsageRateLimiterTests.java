package pl.karolbystrek.kairos.api.analytics.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClock;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsageRateLimiterTests {
    @Test
    void limitsEachCallerAndExpiresItsBudget() {
        var clock = new MutableTestClock();
        var limiter = new UsageRateLimiter(clock);
        for (var request = 0; request < 60; request++) limiter.acquire("first");
        assertThatThrownBy(() -> limiter.acquire("first")).isInstanceOf(ResponseStatusException.class);
        limiter.acquire("second");
        clock.advance(Duration.ofMinutes(1));
        limiter.acquire("first");
    }
}
