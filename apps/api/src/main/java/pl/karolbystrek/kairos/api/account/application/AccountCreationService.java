package pl.karolbystrek.kairos.api.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.exception.AccountConflictException;
import pl.karolbystrek.kairos.api.account.domain.Account;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;


@Service
@RequiredArgsConstructor
public class AccountCreationService {
    private final AccountRepository accounts;

    @Transactional(readOnly = true)
    public boolean identityConflict(String email, String subject) {
        return accounts.identityConflict(email, subject);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Account create(Account account) {
        if (identityConflict(account.getEmail(), account.getProviderSubject())) {
            throw new AccountConflictException("An account with the supplied identity already exists");
        }
        return accounts.saveNew(account);
    }
}
