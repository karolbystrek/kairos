package pl.karolbystrek.kairos.api.authentication.infrastructure.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.authentication.application.exception.SignInThrottledException;
import pl.karolbystrek.kairos.api.util.Hashing;

import java.time.Duration;
import java.util.List;

/**
 * Caps failed staff sign-ins per (email, client address), so a stranger cannot lock an account out from elsewhere,
 * plus a higher per-email ceiling against distributed guessing. Shared by every API instance through Redis.
 */
@Slf4j
@Component
public class SignInThrottle {
    private static final String PREFIX = "kairos:sign-in-failures:";
    // INCR and EXPIRE in one script, so a counter can never be left without its expiry.
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int maximumFailures;
    private final int maximumFailuresPerEmail;
    private final Duration window;

    public SignInThrottle(
            StringRedisTemplate redis,
            @Value("${kairos.authentication.sign-in-throttle.maximum-failures:10}") int maximumFailures,
            @Value("${kairos.authentication.sign-in-throttle.maximum-failures-per-email:100}") int maximumFailuresPerEmail,
            @Value("${kairos.authentication.sign-in-throttle.window:15m}") Duration window
    ) {
        this.redis = redis;
        this.maximumFailures = maximumFailures;
        this.maximumFailuresPerEmail = maximumFailuresPerEmail;
        this.window = window;
    }

    /** Refuses the attempt before any password check while this client or the account has used up its failures. */
    public void requireNotThrottled(String email, String clientAddress) {
        try {
            if (exceeds(redis.opsForValue().get(clientKey(email, clientAddress)), maximumFailures)
                    || exceeds(redis.opsForValue().get(emailKey(email)), maximumFailuresPerEmail)) {
                throw new SignInThrottledException();
            }
        } catch (DataAccessException exception) {
            // shortcut: fails open so a Redis outage does not lock staff out; the per-IP nginx limit still applies.
            log.warn("Sign-in throttle unavailable", exception);
        }
    }

    public void recordFailure(String email, String clientAddress) {
        try {
            var seconds = String.valueOf(window.toSeconds());
            redis.execute(INCREMENT, List.of(clientKey(email, clientAddress)), seconds);
            redis.execute(INCREMENT, List.of(emailKey(email)), seconds);
        } catch (DataAccessException exception) {
            log.warn("Sign-in throttle unavailable", exception);
        }
    }

    /** Forgets every failure of the email, for example after the owner proved control by resetting the password. */
    public void clear(String email) {
        try (var keys = redis.scan(ScanOptions.scanOptions().match(emailPrefix(email) + "*").build())) {
            var found = keys.stream().toList();
            if (!found.isEmpty()) redis.delete(found);
        } catch (DataAccessException exception) {
            log.warn("Sign-in throttle unavailable", exception);
        }
    }

    private static boolean exceeds(String count, int maximum) {
        return count != null && Long.parseLong(count) >= maximum;
    }

    private static String emailPrefix(String normalizedEmail) {
        return PREFIX + Hashing.sha256Hex(normalizedEmail) + ":";
    }

    private static String emailKey(String normalizedEmail) {
        return emailPrefix(normalizedEmail) + "total";
    }

    private static String clientKey(String normalizedEmail, String clientAddress) {
        return emailPrefix(normalizedEmail) + "client:" + clientAddress;
    }
}
