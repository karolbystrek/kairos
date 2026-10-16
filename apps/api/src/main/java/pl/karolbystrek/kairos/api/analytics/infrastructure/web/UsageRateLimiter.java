package pl.karolbystrek.kairos.api.analytics.infrastructure.web;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;

/** Per-caller budget of 60 usage actions per clock minute, shared by every API instance through Redis. */
@Component
@RequiredArgsConstructor
public class UsageRateLimiter {
    static final int REQUESTS_PER_MINUTE = 60;
    // INCR and EXPIRE in one script, so a counter can never be left without its expiry.
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    public void acquire(String key) {
        var minute = clock.instant().getEpochSecond() / 60;
        Long count;
        try {
            count = redis.execute(INCREMENT, List.of("kairos:usage-limit:" + key + ":" + minute), "120");
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Usage collection is unavailable", exception);
        }
        if (count == null || count > REQUESTS_PER_MINUTE) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Usage collection limit reached");
        }
    }
}
