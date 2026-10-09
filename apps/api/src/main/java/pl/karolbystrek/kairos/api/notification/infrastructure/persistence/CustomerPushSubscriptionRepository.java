package pl.karolbystrek.kairos.api.notification.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pl.karolbystrek.kairos.api.notification.domain.CustomerPushSubscription;

import java.util.Optional;
import java.util.UUID;

public interface CustomerPushSubscriptionRepository
        extends JpaRepository<CustomerPushSubscription, UUID> {

    Optional<CustomerPushSubscription> findByEndpointHash(String endpointHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerPushSubscription> findForUpdateByEndpointHash(String endpointHash);

    @Query(value = """
            SELECT id, p256dh_key AS "p256dhKey", encrypted_auth_secret AS "encryptedAuthSecret",
                   auth_secret_nonce AS "authSecretNonce"
            FROM public.verify_push_subscription(:endpointHash)
            """, nativeQuery = true)
    Optional<Capability> verifyCapability(String endpointHash);

    interface Capability {
        UUID getId();
        byte[] getP256dhKey();
        byte[] getEncryptedAuthSecret();
        byte[] getAuthSecretNonce();
    }

}
