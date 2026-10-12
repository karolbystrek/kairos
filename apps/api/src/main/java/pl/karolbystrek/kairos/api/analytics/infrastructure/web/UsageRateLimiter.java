package pl.karolbystrek.kairos.api.analytics.infrastructure.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class UsageRateLimiter {
    private final Clock clock;
    private final Map<String, Budget> budgets = new HashMap<>();

    // shortcut: budgets apply per API instance, use a shared store if deployment needs a global limit.
    public synchronized void acquire(String key) {
        var now = clock.instant().getEpochSecond();
        budgets.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt);
        var budget = budgets.get(key);
        if (budget == null) {
            if (budgets.size() >= 10_000) throw limited();
            budget = new Budget(now + 60);
            budgets.put(key, budget);
        }
        if (budget.requests >= 60) throw limited();
        budget.requests++;
    }

    private static ResponseStatusException limited() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Usage collection limit reached");
    }

    private static final class Budget {
        private final long expiresAt;
        private int requests;
        private Budget(long expiresAt) { this.expiresAt = expiresAt; }
    }
}
