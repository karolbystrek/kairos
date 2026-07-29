package pl.karolbystrek.kairos.api.notification.application.model;

import java.time.Instant;

public record WebPushResult(
        Integer statusCode,
        Instant retryAfter,
        String outcome,
        String diagnostic
) {

    public boolean isAccepted() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }

    public boolean permanentlyInvalidatesSubscription() {
        return statusCode != null && (statusCode == 404 || statusCode == 410);
    }

    public boolean isTransient() {
        return statusCode == null
                || statusCode == 408
                || statusCode == 425
                || statusCode == 429
                || statusCode >= 500;
    }
}
