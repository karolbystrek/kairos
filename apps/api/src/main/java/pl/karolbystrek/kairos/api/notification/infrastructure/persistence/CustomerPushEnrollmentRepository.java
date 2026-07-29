package pl.karolbystrek.kairos.api.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushEnrollment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPushEnrollmentRepository
        extends JpaRepository<CustomerPushEnrollment, UUID> {

    long countByOrderId(UUID orderId);

    boolean existsBySubscriptionIdAndOrderId(UUID subscriptionId, UUID orderId);

    Optional<CustomerPushEnrollment> findBySubscriptionIdAndOrderId(
            UUID subscriptionId,
            UUID orderId
    );

    List<CustomerPushEnrollment> findAllByOrderId(UUID orderId);

    List<CustomerPushEnrollment> findAllBySubscriptionId(UUID subscriptionId);

    void deleteAllBySubscriptionId(UUID subscriptionId);

    void deleteAllByOrderId(UUID orderId);

    void deleteAllBySubscriptionIdAndOrderIdIn(
            UUID subscriptionId,
            Collection<UUID> orderIds
    );
}
