package pl.karolbystrek.kairos.api.account.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pl.karolbystrek.kairos.api.account.domain.Account;
import java.util.*;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import pl.karolbystrek.kairos.api.account.application.exception.AccountConflictException;
public interface AccountRepository extends JpaRepository<Account, UUID> {
    @Query(value = "SELECT public.identity_conflict(:email, :subject)", nativeQuery = true)
    boolean identityConflict(String email, String subject);

    default Account saveNew(Account account) {
        try {
            return saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMostSpecificCause() instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                throw new AccountConflictException("An account with the supplied identity already exists");
            }
            throw exception;
        }
    }

    Optional<Account> findByProviderSubject(String subject);
    Optional<Account> findByEmail(String email);
    boolean existsByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findForUpdateById(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Account> findAllForUpdateByIdIn(Collection<UUID> ids);
}
