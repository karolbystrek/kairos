package pl.karolbystrek.kairos.api.integration.webhook.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http.WebhookHttpResult;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookDeliveryRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebhookDeliveryCompletionService {

    private final WebhookDeliveryRepository deliveryRepository;

    @Transactional
    public boolean complete(
            UUID deliveryId,
            UUID claimToken,
            Instant attemptedAt,
            Instant completedAt,
            WebhookHttpResult result
    ) {
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

    // PostgreSQL TEXT cannot store NUL characters that may occur in arbitrary response bytes.
    private static String replacePostgresNulCharacters(String value) {
        return value == null ? null : value.replace('\0', '\uFFFD');
    }
}
