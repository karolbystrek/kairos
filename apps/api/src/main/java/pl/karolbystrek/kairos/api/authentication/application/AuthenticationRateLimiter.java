package pl.karolbystrek.kairos.api.authentication.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.authentication.application.exception.RateLimitExceededException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

@Component
public class AuthenticationRateLimiter {

    private static final int MAXIMUM_TRACKED_WINDOWS = 10_000;

    private final Clock clock;
    private final int loginAttempts;
    private final Duration loginWindow;
    private final int refreshAttempts;
    private final Duration refreshWindow;
    private final int tenantRegistrationAttempts;
    private final Duration tenantRegistrationWindow;
    private final Map<String, Window> windows = new HashMap<>();
    private final NavigableSet<WindowExpiry> expiries = new TreeSet<>(
        Comparator.comparing(WindowExpiry::resetAt).thenComparing(WindowExpiry::key)
    );

    AuthenticationRateLimiter(
        Clock clock,
        @Value("${kairos.authentication.rate-limit.login-attempts:10}") int loginAttempts,
        @Value("${kairos.authentication.rate-limit.login-window:PT5M}") Duration loginWindow,
        @Value("${kairos.authentication.rate-limit.refresh-attempts:30}") int refreshAttempts,
        @Value("${kairos.authentication.rate-limit.refresh-window:PT1M}") Duration refreshWindow,
        @Value("${kairos.authentication.rate-limit.tenant-registration-attempts:5}")
        int tenantRegistrationAttempts,
        @Value("${kairos.authentication.rate-limit.tenant-registration-window:PT1H}")
        Duration tenantRegistrationWindow
    ) {
        if (loginAttempts < 1 || refreshAttempts < 1 || tenantRegistrationAttempts < 1) {
            throw new IllegalArgumentException("Authentication rate limits must be positive");
        }
        if (loginWindow == null || !loginWindow.isPositive()
            || refreshWindow == null || !refreshWindow.isPositive()
            || tenantRegistrationWindow == null || !tenantRegistrationWindow.isPositive()) {
            throw new IllegalArgumentException("Authentication rate-limit windows must be positive");
        }
        this.clock = clock;
        this.loginAttempts = loginAttempts;
        this.loginWindow = loginWindow;
        this.refreshAttempts = refreshAttempts;
        this.refreshWindow = refreshWindow;
        this.tenantRegistrationAttempts = tenantRegistrationAttempts;
        this.tenantRegistrationWindow = tenantRegistrationWindow;
    }

    public void checkLogin(String clientAddress, String normalizedUsername) {
        check(
            "login-client:" + fingerprint(clientAddress),
            loginAttempts * 5,
            loginWindow
        );
        check(
            "login-account:" + fingerprint(normalizedUsername),
            loginAttempts * 3,
            loginWindow
        );
        check(
            "login-pair:" + fingerprint(clientAddress + '\0' + normalizedUsername),
            loginAttempts,
            loginWindow
        );
    }

    public void loginSucceeded(String clientAddress, String normalizedUsername) {
        synchronized (windows) {
            remove("login-account:" + fingerprint(normalizedUsername));
            remove("login-pair:" + fingerprint(clientAddress + '\0' + normalizedUsername));
        }
    }

    public void checkRefresh(String clientAddress, String refreshCredential) {
        check("refresh-client:" + fingerprint(clientAddress), refreshAttempts * 10, refreshWindow);
        check(
            "refresh-credential:" + fingerprint(refreshCredential == null ? "" : refreshCredential),
            refreshAttempts,
            refreshWindow
        );
    }

    public void checkTenantRegistration(String clientAddress) {
        check(
            "tenant-registration-client:" + fingerprint(clientAddress),
            tenantRegistrationAttempts,
            tenantRegistrationWindow
        );
    }

    private void check(String key, int limit, Duration duration) {
        var now = clock.instant();
        Window window;
        synchronized (windows) {
            removeExpired(now);
            var existing = windows.get(key);
            if (existing == null) {
                if (windows.size() >= MAXIMUM_TRACKED_WINDOWS) {
                    var retryAfter = Duration.between(now, expiries.first().resetAt());
                    throw new RateLimitExceededException(retryAfter);
                }
                window = new Window(1, now.plus(duration));
                windows.put(key, window);
                expiries.add(new WindowExpiry(key, window.resetAt()));
            }
            else {
                window = new Window(existing.attempts() + 1, existing.resetAt());
                windows.put(key, window);
            }
        }

        if (window.attempts() > limit) {
            throw new RateLimitExceededException(Duration.between(now, window.resetAt()));
        }
    }

    private void remove(String key) {
        var removed = windows.remove(key);
        if (removed != null) {
            expiries.remove(new WindowExpiry(key, removed.resetAt()));
        }
    }

    private void removeExpired(Instant now) {
        while (!expiries.isEmpty() && !expiries.first().resetAt().isAfter(now)) {
            var expiry = expiries.pollFirst();
            var current = windows.get(expiry.key());
            if (current != null && current.resetAt().equals(expiry.resetAt())) {
                windows.remove(expiry.key());
            }
        }
    }

    private static String fingerprint(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Window(int attempts, Instant resetAt) {
    }

    private record WindowExpiry(String key, Instant resetAt) {
    }
}
