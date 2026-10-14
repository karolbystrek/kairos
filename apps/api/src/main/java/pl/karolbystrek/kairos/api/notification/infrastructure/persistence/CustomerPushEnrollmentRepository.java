package pl.karolbystrek.kairos.api.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushEnrollment;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CustomerPushEnrollmentRepository
        extends JpaRepository<CustomerPushEnrollment, UUID> {

    @Query(value = "SELECT public.count_push_enrollments(:trackingReference)", nativeQuery = true)
    long countForTrackingReference(UUID trackingReference);

    List<CustomerPushEnrollment> findAllByOrderId(UUID orderId);

    List<CustomerPushEnrollment> findAllBySubscriptionId(UUID subscriptionId);

    void deleteAllBySubscriptionId(UUID subscriptionId);

    void deleteAllByOrderId(UUID orderId);

    void deleteAllBySubscriptionIdAndOrderIdIn(
            UUID subscriptionId,
            Collection<UUID> orderIds
    );
}
