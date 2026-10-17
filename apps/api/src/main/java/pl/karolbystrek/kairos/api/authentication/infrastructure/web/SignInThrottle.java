package pl.karolbystrek.kairos.api.authentication.infrastructure.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private static final String TOTAL = "total";
    // One hash per email, so clearing it is a single DEL. HINCRBY and HEXPIRE run in one script, so a counter can
    // never be left without its expiry; the hash disappears once its last field expires.
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local count = redis.call('HINCRBY', KEYS[1], ARGV[1], 1)
            if count == 1 then redis.call('HEXPIRE', KEYS[1], ARGV[2], 'FIELDS', 1, ARGV[1]) end
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
            var counts = redis.<String, String>opsForHash().multiGet(key(email), List.of(clientField(clientAddress), TOTAL));
            if (exceeds(counts.get(0), maximumFailures) || exceeds(counts.get(1), maximumFailuresPerEmail)) {
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
            redis.execute(INCREMENT, List.of(key(email)), clientField(clientAddress), seconds);
            redis.execute(INCREMENT, List.of(key(email)), TOTAL, seconds);
        } catch (DataAccessException exception) {
            log.warn("Sign-in throttle unavailable", exception);
        }
    }

    /** Forgets every failure of the email, for example after the owner proved control by resetting the password. */
    public void clear(String email) {
        try {
            redis.delete(key(email));
        } catch (DataAccessException exception) {
            log.warn("Sign-in throttle unavailable", exception);
        }
    }

    private static boolean exceeds(String count, int maximum) {
        return count != null && Long.parseLong(count) >= maximum;
    }

    private static String key(String normalizedEmail) {
        return PREFIX + Hashing.sha256Hex(normalizedEmail);
    }

    private static String clientField(String clientAddress) {
        return "client:" + clientAddress;
    }
}
