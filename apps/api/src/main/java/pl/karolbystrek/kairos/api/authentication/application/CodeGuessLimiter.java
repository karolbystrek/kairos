package pl.karolbystrek.kairos.api.authentication.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.authentication.application.exception.RegistrationValidationException;
import pl.karolbystrek.kairos.api.util.Hashing;

import java.time.Duration;
import java.util.List;

/**
 * Cumulative cap on wrong emailed codes per email. Re-issuing a code resets its own attempt counter, so without
 * this a patient guesser gets unlimited tries; the lock survives re-issue and shares Redis across API instances.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeGuessLimiter {
    static final int MAXIMUM_FAILURES = 15;
    static final Duration LOCK = Duration.ofHours(1);
    // The expiry is (re)armed on the first and on the locking failure, so the lock lasts a full hour.
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 or count == tonumber(ARGV[2]) then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;

    public void requireNotLocked(String purpose, String email) {
        try {
            var count = redis.opsForValue().get(key(purpose, email));
            if (count != null && Long.parseLong(count) >= MAXIMUM_FAILURES) {
                throw new RegistrationValidationException(HttpStatus.TOO_MANY_REQUESTS, "code",
                    "Too many incorrect codes. Try again in an hour.");
            }
        } catch (DataAccessException exception) {
            // shortcut: fails open so a Redis outage does not block recovery; per-code attempts still apply.
            log.warn("Code guess limiter unavailable", exception);
        }
    }

    public void recordFailure(String purpose, String email) {
        try {
            redis.execute(INCREMENT, List.of(key(purpose, email)),
                String.valueOf(LOCK.toSeconds()), String.valueOf(MAXIMUM_FAILURES));
        } catch (DataAccessException exception) {
            log.warn("Code guess limiter unavailable", exception);
        }
    }

    public void clear(String purpose, String email) {
        try {
            redis.delete(key(purpose, email));
        } catch (DataAccessException exception) {
            log.warn("Code guess limiter unavailable", exception);
        }
    }

    private static String key(String purpose, String email) {
        return "kairos:code-failures:" + purpose + ":" + Hashing.sha256Hex(email);
    }
}
