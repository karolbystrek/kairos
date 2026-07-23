package pl.karolbystrek.kairos.api.authentication.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.authentication.application.exception.InvalidLoginException;
import pl.karolbystrek.kairos.api.authentication.application.model.IssuedSession;
import pl.karolbystrek.kairos.api.authentication.application.model.PasswordVerificationFallback;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class LocalAuthenticationService {

    private static final int MAXIMUM_BCRYPT_PASSWORD_BYTES = 72;

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationSessionService sessionService;
    private final AuthenticationRateLimiter rateLimiter;
    private final PasswordVerificationFallback passwordVerificationFallback;

    public IssuedSession authenticate(String username, String password, String clientAddress) {
        var normalizedUsername = normalizeUsername(username);
        rateLimiter.checkLogin(clientAddress, normalizedUsername);

        var account = accountRepository.findByUsername(normalizedUsername).orElse(null);
        var storedHash = account != null && account.getPasswordHash() != null
            ? account.getPasswordHash()
            : passwordVerificationFallback.encodedCandidate();
        var acceptableLength = password != null
            && password.getBytes(StandardCharsets.UTF_8).length <= MAXIMUM_BCRYPT_PASSWORD_BYTES;
        var passwordToVerify = acceptableLength ? password : passwordVerificationFallback.candidate();
        var passwordMatches = passwordEncoder.matches(passwordToVerify, storedHash);

        if (account == null || !acceptableLength || !passwordMatches || account.getPasswordHash() == null) {
            log.warn("Rejected local login attempt");
            throw new InvalidLoginException();
        }

        var principal = new StaffPrincipal(
            account.getId(),
            account.getTenantId(),
            account.getTenantRole()
        );
        try {
            var session = sessionService.start(principal);
            rateLimiter.loginSucceeded(clientAddress, normalizedUsername);
            log.info("Authenticated local account {}", account.getId());
            return session;
        }
        catch (StaffAccessDeniedException exception) {
            log.warn("Rejected ineligible local account {}", account.getId());
            throw new InvalidLoginException();
        }
    }

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
}
