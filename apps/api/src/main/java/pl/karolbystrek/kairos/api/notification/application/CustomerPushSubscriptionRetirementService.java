package pl.karolbystrek.kairos.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushDeliveryStatus;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushDeliveryRepository;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushEnrollmentRepository;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushSubscriptionRepository;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class CustomerPushSubscriptionRetirementService {

    private final CustomerPushSubscriptionRepository subscriptionRepository;
    private final CustomerPushEnrollmentRepository enrollmentRepository;
    private final CustomerPushDeliveryRepository deliveryRepository;

    void retire(UUID subscriptionId, Instant now) {
        var subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null) {
            return;
        }
        deliveryRepository
                .findAllBySubscriptionIdAndStatus(
                        subscriptionId,
                        CustomerPushDeliveryStatus.PENDING
                )
                .forEach(delivery -> delivery.cancel(now));
        enrollmentRepository.deleteAllBySubscriptionId(subscriptionId);
        subscriptionRepository.delete(subscription);
    }
}
