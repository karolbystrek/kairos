package pl.karolbystrek.kairos.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushEnrollmentRepository;
import pl.karolbystrek.kairos.api.notification.infrastructure.persistence.CustomerPushSubscriptionRepository;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class CustomerPushSubscriptionRetirementService {

    private final CustomerPushSubscriptionRepository subscriptionRepository;
    private final CustomerPushEnrollmentRepository enrollmentRepository;
    private final org.springframework.jdbc.core.JdbcTemplate database;
    private final jakarta.persistence.EntityManager entityManager;

    void retire(UUID subscriptionId, Instant now) {
        if (subscriptionId == null) {
            return;
        }
        var subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null) {
            return;
        }
        entityManager.flush();
        database.queryForObject("SELECT public.cancel_push_deliveries(?, NULL, ?)", Integer.class,
                subscriptionId, java.sql.Timestamp.from(now));
        enrollmentRepository.deleteAllBySubscriptionId(subscriptionId);
        subscriptionRepository.delete(subscription);
    }
}
