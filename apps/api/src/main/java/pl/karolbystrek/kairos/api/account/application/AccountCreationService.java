package pl.karolbystrek.kairos.api.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.exception.AccountConflictException;
import pl.karolbystrek.kairos.api.account.domain.Account;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountCreationService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public Account createAdministrator(
        UUID tenantId,
        String username,
        String email,
        String password,
        String displayName
    ) {
        return create(
            tenantId,
            username,
            email,
            password,
            displayName,
            true
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Account createMember(
        UUID tenantId,
        String username,
        String email,
        String password,
        String displayName
    ) {
        return create(
            tenantId,
            username,
            email,
            password,
            displayName,
            false
        );
    }

    private Account create(
        UUID tenantId,
        String username,
        String email,
        String password,
        String displayName,
        boolean administrator
    ) {
        var normalizedUsername = username.strip().toLowerCase(Locale.ROOT);
        var normalizedEmail = email == null || email.isBlank()
            ? null
            : email.strip().toLowerCase(Locale.ROOT);
        var normalizedDisplayName = displayName.strip();
        requireAvailableIdentifiers(normalizedUsername, normalizedEmail);

        var now = clock.instant();
        var passwordHash = passwordEncoder.encode(password);
        var account = administrator
            ? Account.provisionAdministrator(
                tenantId,
                normalizedUsername,
                normalizedEmail,
                passwordHash,
                normalizedDisplayName,
                now
            )
            : Account.provisionMember(
                tenantId,
                normalizedUsername,
                normalizedEmail,
                passwordHash,
                normalizedDisplayName,
                now
            );

        try {
            return accountRepository.saveAndFlush(account);
        }
        catch (DataIntegrityViolationException exception) {
            throw new AccountConflictException(
                "An account with the supplied identity already exists",
                exception
            );
        }
    }

    private void requireAvailableIdentifiers(String username, String email) {
        if (accountRepository.existsByUsername(username)
            || (email != null && accountRepository.existsByEmail(email))) {
            throw new AccountConflictException(
                "An account with the supplied identity already exists"
            );
        }
    }
}
