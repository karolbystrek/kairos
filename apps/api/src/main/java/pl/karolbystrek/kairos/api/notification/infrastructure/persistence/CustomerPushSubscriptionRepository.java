package pl.karolbystrek.kairos.api.notification.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushSubscription;

import java.util.Optional;
import java.util.UUID;

public interface CustomerPushSubscriptionRepository
        extends JpaRepository<CustomerPushSubscription, UUID> {

    Optional<CustomerPushSubscription> findByEndpointHash(String endpointHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerPushSubscription> findForUpdateByEndpointHash(String endpointHash);

}
