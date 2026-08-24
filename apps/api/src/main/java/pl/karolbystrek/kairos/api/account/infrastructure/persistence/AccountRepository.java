package pl.karolbystrek.kairos.api.account.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.account.domain.Account;
import pl.karolbystrek.kairos.api.account.domain.AccountKind;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findForUpdateById(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findForUpdateByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Account> findAllForUpdateByKindOrderById(AccountKind kind);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Account> findAllForUpdateByIdIn(Collection<UUID> accountIds);
}
