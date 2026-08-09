package pl.karolbystrek.kairos.api.account.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AccountAuthenticatorRepository {

    private final EntityManager entityManager;

    public int deleteExternalIdentities(Collection<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return 0;
        }
        return entityManager.createNativeQuery("""
                DELETE FROM external_identities
                WHERE account_id IN (:accountIds)
                """)
            .setParameter("accountIds", accountIds)
            .executeUpdate();
    }
}
