package pl.karolbystrek.kairos.api.analytics.infrastructure.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import pl.karolbystrek.kairos.api.integration.testsupport.MutableTestClock;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsageRateLimiterTests {
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connections;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connections = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connections.afterPropertiesSet();
        connections.start();
    }

    @AfterAll
    static void stopRedis() {
        connections.destroy();
        REDIS.stop();
    }

    @Test
    void sharesEachCallersBudgetAcrossInstancesAndExpiresIt() {
        var clock = new MutableTestClock();
        var redis = new StringRedisTemplate(connections);
        // Two limiters stand in for two API instances behind a load balancer.
        var first = new UsageRateLimiter(redis, clock);
        var second = new UsageRateLimiter(redis, clock);
        for (var request = 0; request < UsageRateLimiter.REQUESTS_PER_MINUTE / 2; request++) {
            first.acquire("caller");
            second.acquire("caller");
        }
        assertThatThrownBy(() -> first.acquire("caller")).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        second.acquire("other");
        clock.advance(Duration.ofMinutes(1));
        first.acquire("caller");
        assertThat(redis.keys("kairos:usage-limit:*")).allSatisfy(key -> assertThat(redis.getExpire(key)).isPositive());
    }
}
