package pl.karolbystrek.kairos.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.notification.application.model.WebPushResult;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushDeliveryRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class CustomerPushDeliveryCompletionService {

    private final CustomerPushDeliveryRepository deliveryRepository;
    private final CustomerPushSubscriptionRetirementService retirementService;
    private final CustomerNotificationProperties properties;

    @Transactional
    public boolean complete(
            UUID deliveryId,
            UUID claimToken,
            UUID subscriptionId,
            Instant completedAt,
            WebPushResult result
    ) {
        var delivery = deliveryRepository.findForUpdateByIdAndClaimToken(
                deliveryId,
                claimToken
        ).orElse(null);
        if (delivery == null) {
            return false;
        }
        if (!delivery.getDeadlineAt().isAfter(completedAt)) {
            delivery.expire(claimToken, completedAt, "Customer Push freshness deadline has passed");
            return true;
        }
        if (result.isAccepted()) {
            delivery.accept(claimToken, completedAt, result.statusCode());
            return true;
        }
        if (result.permanentlyInvalidatesSubscription()) {
            delivery.deadLetter(
                    claimToken,
                    completedAt,
                    result.statusCode(),
                    "SUBSCRIPTION_INVALID",
                    safeDiagnostic(result.diagnostic())
            );
            retirementService.retire(subscriptionId, completedAt);
            return true;
        }
        if (result.isTransient()
                && delivery.getAttemptCount() < properties.delivery().maximumAttempts()) {
            var retryAt = nextAttempt(delivery.getAttemptCount(), completedAt, result.retryAfter());
            if (retryAt.isBefore(delivery.getDeadlineAt())) {
                delivery.retry(
                        claimToken,
                        retryAt,
                        result.statusCode(),
                        result.outcome(),
                        safeDiagnostic(result.diagnostic())
                );
                return true;
            }
            delivery.expire(
                    claimToken,
                    completedAt,
                    "Customer Push retry would exceed its freshness deadline"
            );
            return true;
        }
        delivery.deadLetter(
                claimToken,
                completedAt,
                result.statusCode(),
                result.outcome() == null ? "DELIVERY_FAILED" : result.outcome(),
                safeDiagnostic(result.diagnostic())
        );
        return true;
    }

    private Instant nextAttempt(int attemptCount, Instant now, Instant retryAfter) {
        if (retryAfter != null && retryAfter.isAfter(now)) {
            return retryAfter;
        }
        var exponent = Math.min(30, Math.max(0, attemptCount - 1));
        var initialMillis = properties.delivery().initialRetryDelay().toMillis();
        var maximumMillis = properties.delivery().maximumRetryDelay().toMillis();
        var cappedMillis = Math.min(maximumMillis, Math.multiplyExact(
                initialMillis,
                1L << exponent
        ));
        var jitterMillis = ThreadLocalRandom.current().nextLong(cappedMillis + 1);
        return now.plusMillis(jitterMillis);
    }

    private static String safeDiagnostic(String value) {
        if (value == null) {
            return null;
        }
        var sanitized = value.replace('\0', '\uFFFD');
        return sanitized.length() <= 1024 ? sanitized : sanitized.substring(0, 1024);
    }
}
