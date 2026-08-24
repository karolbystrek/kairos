package pl.karolbystrek.kairos.api.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.exception.AccountConflictException;
import pl.karolbystrek.kairos.api.account.application.exception.InvalidAccountRequestException;
import pl.karolbystrek.kairos.api.account.domain.Account;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

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
        String password
    ) {
        return create(
            tenantId,
            username,
            email,
            password,
            true
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Account createMember(
        UUID tenantId,
        String username,
        String email,
        String password
    ) {
        return create(
            tenantId,
            username,
            email,
            password,
            false
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Account createPlatformOperator(
        String username,
        String email,
        String password
    ) {
        var normalizedUsername = username == null
            ? ""
            : username.strip().toLowerCase(Locale.ROOT);
        if (normalizedUsername.isEmpty() || normalizedUsername.length() > 120) {
            throw new InvalidAccountRequestException("A valid username is required");
        }
        if (email == null || email.isBlank()) {
            throw new InvalidAccountRequestException("Email is required");
        }
        var normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        if (normalizedEmail.length() > 254 || !normalizedEmail.contains("@")) {
            throw new InvalidAccountRequestException("A valid email is required");
        }
        if (password == null
            || password.length() < 12
            || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidAccountRequestException("A valid password is required");
        }
        requireAvailableIdentifiers(normalizedUsername, normalizedEmail);

        var account = Account.provisionPlatformOperator(
            normalizedUsername,
            normalizedEmail,
            passwordEncoder.encode(password),
            clock.instant()
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

    private Account create(
        UUID tenantId,
        String username,
        String email,
        String password,
        boolean administrator
    ) {
        var normalizedUsername = username.strip().toLowerCase(Locale.ROOT);
        if (email == null || email.isBlank()) {
            throw new InvalidAccountRequestException("Email is required");
        }
        var normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        requireAvailableIdentifiers(normalizedUsername, normalizedEmail);

        var now = clock.instant();
        var passwordHash = passwordEncoder.encode(password);
        var account = administrator
            ? Account.provisionAdministrator(
                tenantId,
                normalizedUsername,
                normalizedEmail,
                passwordHash,
                now
            )
            : Account.provisionMember(
                tenantId,
                normalizedUsername,
                normalizedEmail,
                passwordHash,
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
            || accountRepository.existsByEmail(email)) {
            throw new AccountConflictException(
                "An account with the supplied identity already exists"
            );
        }
    }
}
