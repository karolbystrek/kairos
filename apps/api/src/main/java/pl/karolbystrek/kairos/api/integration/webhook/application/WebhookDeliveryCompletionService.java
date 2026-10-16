package pl.karolbystrek.kairos.api.integration.webhook.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http.WebhookHttpResult;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookDeliveryRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.persistence.infrastructure.WorkerOperation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebhookDeliveryCompletionService {

    // Total attempts including the first; the delay after attempt n is RETRY_DELAYS[n - 1].
    static final int MAXIMUM_ATTEMPTS = 5;
    static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofMinutes(30));

    private final WebhookDeliveryRepository deliveryRepository;
    private final DatabaseAccessContext databaseAccess;

    @Transactional
    public boolean complete(
            UUID deliveryId,
            UUID claimToken,
            Instant attemptedAt,
            Instant completedAt,
            WebhookHttpResult result
    ) {
        if (!databaseAccess.worker(WorkerOperation.WEBHOOK_COMPLETE, deliveryId, claimToken)) return false;
        var delivery = deliveryRepository.findForUpdateByIdAndClaimToken(deliveryId, claimToken);
        if (delivery.isEmpty()) {
            return false;
        }
        if (result.isSuccessful()) {
            delivery.get().succeed(
                    claimToken,
                    attemptedAt,
                    completedAt,
                    result.statusCode(),
                    replacePostgresNulCharacters(result.responseBody()),
                    result.responseTruncated()
            );
        } else if (isTransient(result) && delivery.get().getAttemptCount() < MAXIMUM_ATTEMPTS) {
            delivery.get().retry(
                    claimToken,
                    attemptedAt,
                    completedAt.plus(RETRY_DELAYS.get(delivery.get().getAttemptCount() - 1)),
                    result.statusCode(),
                    replacePostgresNulCharacters(result.responseBody()),
                    result.responseTruncated(),
                    result.errorType() == null ? "DELIVERY_FAILED" : result.errorType(),
                    replacePostgresNulCharacters(result.errorDetail())
            );
        } else {
            delivery.get().deadLetter(
                    claimToken,
                    attemptedAt,
                    completedAt,
                    result.statusCode(),
                    replacePostgresNulCharacters(result.responseBody()),
                    result.responseTruncated(),
                    result.errorType() == null ? "DELIVERY_FAILED" : result.errorType(),
                    replacePostgresNulCharacters(result.errorDetail())
            );
        }
        return true;
    }

    // A recipient outage may heal; 4xx other than 429, blocked destinations and signing errors never will.
    private static boolean isTransient(WebhookHttpResult result) {
        var status = result.statusCode();
        if (status != null) return status == 429 || status >= 500;
        return "TIMEOUT".equals(result.errorType()) || "NETWORK_ERROR".equals(result.errorType());
    }

    // PostgreSQL TEXT cannot store NUL characters that may occur in arbitrary response bytes.
    private static String replacePostgresNulCharacters(String value) {
        return value == null ? null : value.replace('\0', '\uFFFD');
    }
}
