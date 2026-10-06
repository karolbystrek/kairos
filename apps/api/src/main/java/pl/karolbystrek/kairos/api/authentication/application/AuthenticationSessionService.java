package pl.karolbystrek.kairos.api.authentication.application;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.model.PanelPrincipal;
import pl.karolbystrek.kairos.api.account.application.port.AccountSessionRevoker;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient;

import java.time.Clock;
import java.util.Collection;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationSessionService implements AccountSessionRevoker {
    public static final String IDENTITY = "kairos.identity";
    public static final String SIGNED_IN_AT = "kairos.signedInAt";
    private final JdbcTemplate database;
    private final AccountRepository accounts;
    private final ZitadelClient provider;
    private final Clock clock;

    public void establish(HttpServletRequest request, PanelPrincipal principal, ZitadelClient.ProviderSession identity) {
        var old = request.getSession(false);
        if (old != null) old.invalidate();
        var session = request.getSession(true);
        session.setAttribute(IDENTITY, identity);
        session.setAttribute(SIGNED_IN_AT, clock.instant());
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal.getName());
    }

    public void logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) return;
        var identity = (ZitadelClient.ProviderSession) session.getAttribute(IDENTITY);
        session.invalidate();
        if (identity != null) provider.terminate(identity);
    }

    @Override
    @Transactional
    public void revokeAll(UUID accountId) {
        accounts.findForUpdateById(accountId).ifPresent(account -> account.revokeAuthentication(clock.instant()));
        // Bulk deletion shares the Account transaction; JDBC session attributes cascade.
        database.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", accountId.toString());
    }
    @Override
    @Transactional
    public void revokeAll(Collection<UUID> ids) { ids.stream().sorted().forEach(this::revokeAll); }

    @Transactional
    public void logoutAll(PanelPrincipal principal) { revokeAll(principal.accountId()); }
}
