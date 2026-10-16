package pl.karolbystrek.kairos.api.authentication.infrastructure.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.authentication.application.exception.SignInThrottledException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/** Per-account cap on failed staff sign-ins, shared by every API instance through Redis. */
@Slf4j
@Component
public class SignInThrottle {
    // INCR and EXPIRE in one script, so a counter can never be left without its expiry.
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int maximumFailures;
    private final Duration window;

    public SignInThrottle(
            StringRedisTemplate redis,
            @Value("${kairos.authentication.sign-in-throttle.maximum-failures:10}") int maximumFailures,
            @Value("${kairos.authentication.sign-in-throttle.window:15m}") Duration window
    ) {
        this.redis = redis;
        this.maximumFailures = maximumFailures;
        this.window = window;
    }

    /** Refuses the attempt before any password check while the account has used up its failures. */
    public void requireNotThrottled(String email) {
        String count;
        try {
            count = redis.opsForValue().get(key(email));
        } catch (DataAccessException exception) {
            // shortcut: fails open so a Redis outage does not lock staff out; the per-IP nginx limit still applies.
            log.warn("Sign-in throttle unavailable", exception);
            return;
        }
        if (count != null && Long.parseLong(count) >= maximumFailures) {
            throw new SignInThrottledException();
        }
    }

    public void recordFailure(String email) {
        try {
            redis.execute(INCREMENT, List.of(key(email)), String.valueOf(window.toSeconds()));
        } catch (DataAccessException exception) {
            log.warn("Sign-in throttle unavailable", exception);
        }
    }

    private static String key(String normalizedEmail) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(normalizedEmail.getBytes(StandardCharsets.UTF_8));
            return "kairos:sign-in-failures:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
