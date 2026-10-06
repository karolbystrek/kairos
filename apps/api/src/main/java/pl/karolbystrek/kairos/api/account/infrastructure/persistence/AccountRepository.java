package pl.karolbystrek.kairos.api.account.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.account.domain.Account;
import java.util.*;
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByProviderSubject(String subject);
    Optional<Account> findByEmail(String email);
    boolean existsByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findForUpdateById(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Account> findAllForUpdateByIdIn(Collection<UUID> ids);
}
