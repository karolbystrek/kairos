package pl.karolbystrek.kairos.api.authentication.infrastructure.persistence;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import pl.karolbystrek.kairos.api.account.domain.Account;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface StaffAuthenticationRepository extends Repository<Account, UUID> {

    @Query(value = """
        SELECT account_id AS "accountId", tenant_id AS "tenantId", tenant_role AS "tenantRole",
            authentication_cutoff AS "authenticationCutoff"
        FROM public.staff_authentication(:subject)
        """, nativeQuery = true)
    Optional<Credentials> findCredentials(String subject);

    interface Credentials {
        UUID getAccountId();
        UUID getTenantId();
        String getTenantRole();
        OffsetDateTime getAuthenticationCutoff();
    }
}
