package pl.karolbystrek.kairos.api.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import pl.karolbystrek.kairos.api.account.domain.Account;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.account.application.exception.AccountConflictException;
import java.time.Clock;
import java.util.*;
@Service
@RequiredArgsConstructor
public class AccountCreationService {
    private final AccountRepository accounts;
    private final Clock clock;
    @Transactional(propagation = Propagation.MANDATORY)
    public Account createAdministrator(UUID tenantId, String email, String subject) {
        requireAvailable(email, subject);
        return accounts.saveAndFlush(Account.provisionAdministrator(tenantId, email, subject, clock.instant()));
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public Account createMember(UUID tenantId, String email, String subject) {
        requireAvailable(email, subject);
        return accounts.saveAndFlush(Account.provisionMember(tenantId, email, subject, clock.instant()));
    }
    private void requireAvailable(String email, String subject) {
        if (accounts.existsByEmail(email) || accounts.findByProviderSubject(subject).isPresent()) {
            throw new AccountConflictException("An account with the supplied identity already exists");
        }
    }
}
