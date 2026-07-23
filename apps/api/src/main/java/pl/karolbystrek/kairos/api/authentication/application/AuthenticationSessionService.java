package pl.karolbystrek.kairos.api.authentication.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.application.port.AccountSessionRevoker;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.authentication.application.exception.InvalidRefreshCredentialException;
import pl.karolbystrek.kairos.api.authentication.application.exception.RefreshCredentialReuseException;
import pl.karolbystrek.kairos.api.authentication.application.model.IssuedSession;
import pl.karolbystrek.kairos.api.authentication.domain.RefreshSession;
import pl.karolbystrek.kairos.api.authentication.infrastructure.config.AuthenticationProperties;
import pl.karolbystrek.kairos.api.authentication.infrastructure.jwt.AccessTokenIssuer;
import pl.karolbystrek.kairos.api.authentication.infrastructure.persistence.RefreshSessionRepository;
import pl.karolbystrek.kairos.api.authentication.infrastructure.persistence.RefreshSessionRepository.RefreshSessionReference;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationSessionService implements AccountSessionRevoker {

    private final AccountRepository accountRepository;
    private final RefreshSessionRepository sessionRepository;
    private final RefreshCredentialService credentialService;
    private final StaffAccessService staffAccessService;
    private final AccessTokenIssuer accessTokenIssuer;
    private final AuthenticationProperties properties;
    private final Clock clock;

    @Transactional
    public IssuedSession start(StaffPrincipal authenticatedAccount) {
        staffAccessService.resolveForUpdate(authenticatedAccount);
        var now = clock.instant();
        var credential = credentialService.generate();
        var session = RefreshSession.start(
            authenticatedAccount.accountId(),
            credential.hash(),
            now,
            now.plus(properties.refresh().absoluteLifetime())
        );
        sessionRepository.save(session);
        log.info("Created refresh session {} for account {}", session.getId(), authenticatedAccount.accountId());
        return grant(authenticatedAccount, credential.value(), session, now);
    }

    @Transactional(noRollbackFor = InvalidRefreshCredentialException.class)
    public IssuedSession rotate(String presentedCredential) {
        if (!StringUtils.hasText(presentedCredential)) {
            throw new InvalidRefreshCredentialException();
        }

        var credentialHash = credentialService.hash(presentedCredential);
        var discovered = sessionRepository.findReferenceByRefreshTokenHash(credentialHash)
            .orElseThrow(InvalidRefreshCredentialException::new);
        var account = accountRepository.findForUpdateById(discovered.getAccountId())
            .orElseThrow(InvalidRefreshCredentialException::new);

        var familyRoot = sessionRepository.findForUpdateById(discovered.getTokenFamilyId())
            .orElseThrow(InvalidRefreshCredentialException::new);
        var current = sessionRepository.findForUpdateByRefreshTokenHash(credentialHash)
            .orElseThrow(InvalidRefreshCredentialException::new);
        if (!sameFamily(discovered, familyRoot, current)) {
            throw new InvalidRefreshCredentialException();
        }

        var now = clock.instant();
        if (current.wasConsumed()) {
            sessionRepository.revokeFamily(current.getTokenFamilyId(), now);
            log.warn("Detected refresh credential reuse for token family {}", current.getTokenFamilyId());
            throw new RefreshCredentialReuseException();
        }
        if (current.getRevokedAt() != null) {
            throw new InvalidRefreshCredentialException();
        }
        if (current.isUnavailableAt(now, properties.refresh().idleLifetime())) {
            current.revoke(now);
            throw new InvalidRefreshCredentialException();
        }

        var principal = new StaffPrincipal(
            account.getId(),
            account.getTenantId(),
            account.getTenantRole()
        );
        try {
            staffAccessService.resolveForUpdate(principal);
        }
        catch (StaffAccessDeniedException exception) {
            sessionRepository.revokeAllForAccount(account.getId(), now);
            throw new InvalidRefreshCredentialException();
        }

        var replacementCredential = credentialService.generate();
        var replacement = current.replacement(replacementCredential.hash(), now);
        sessionRepository.saveAndFlush(replacement);
        current.consume(replacement.getId(), now);
        log.info("Rotated refresh session {} to {}", current.getId(), replacement.getId());
        return grant(principal, replacementCredential.value(), replacement, now);
    }

    @Transactional
    public void logout(StaffPrincipal principal, String presentedCredential) {
        if (principal == null || !StringUtils.hasText(presentedCredential)) {
            return;
        }

        var credentialHash = credentialService.hash(presentedCredential);
        var discovered = sessionRepository.findReferenceByRefreshTokenHash(credentialHash);
        if (discovered.isEmpty() || !principal.accountId().equals(discovered.get().getAccountId())) {
            return;
        }
        if (accountRepository.findForUpdateById(principal.accountId()).isEmpty()) {
            return;
        }
        sessionRepository.findForUpdateById(discovered.get().getTokenFamilyId())
            .orElseThrow(InvalidRefreshCredentialException::new);
        var session = sessionRepository.findForUpdateByRefreshTokenHash(credentialHash)
            .orElseThrow(InvalidRefreshCredentialException::new);
        var now = clock.instant();
        if (session.wasConsumed()) {
            sessionRepository.revokeFamily(session.getTokenFamilyId(), now);
        }
        else {
            session.revoke(now);
        }
        log.info("Revoked current refresh session for account {}", principal.accountId());
    }

    @Transactional
    public void logoutAll(StaffPrincipal principal) {
        if (principal == null || accountRepository.findForUpdateById(principal.accountId()).isEmpty()) {
            return;
        }
        revokeAll(principal.accountId());
        log.info("Revoked all refresh sessions for account {}", principal.accountId());
    }

    @Override
    @Transactional
    public void revokeAll(UUID accountId) {
        sessionRepository.revokeAllForAccount(accountId, clock.instant());
    }

    private IssuedSession grant(
        StaffPrincipal principal,
        String refreshCredential,
        RefreshSession session,
        Instant now
    ) {
        var idleExpiresAt = now.plus(properties.refresh().idleLifetime());
        var cookieExpiresAt = idleExpiresAt.isBefore(session.getExpiresAt())
            ? idleExpiresAt
            : session.getExpiresAt();
        return new IssuedSession(
            principal,
            accessTokenIssuer.issue(principal),
            refreshCredential,
            cookieExpiresAt
        );
    }

    private static boolean sameFamily(
        RefreshSessionReference discovered,
        RefreshSession familyRoot,
        RefreshSession current
    ) {
        return discovered.getId().equals(current.getId())
            && discovered.getAccountId().equals(current.getAccountId())
            && discovered.getTokenFamilyId().equals(current.getTokenFamilyId())
            && familyRoot.getId().equals(current.getTokenFamilyId())
            && familyRoot.getAccountId().equals(current.getAccountId());
    }
}
