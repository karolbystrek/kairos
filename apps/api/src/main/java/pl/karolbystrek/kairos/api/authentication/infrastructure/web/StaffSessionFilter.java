package pl.karolbystrek.kairos.api.authentication.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.authentication.application.AuthenticationSessionService;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
public class StaffSessionFilter extends OncePerRequestFilter {
    private final ZitadelClient provider;
    private final AccountRepository accounts;
    private final StaffAccessService access;
    private final HttpSessionIdResolver cookies;
    private final SecurityProblemDetailsHandler errors;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/tracked-orders/") || path.startsWith("/customer-notifications/")
            || path.startsWith("/external/") || path.startsWith("/actuator/")
            || List.of("/auth/v1/csrf", "/auth/v1/login", "/auth/v1/logout", "/tenant-registrations/v1",
                "/account-invitation-redemptions/v1", "/account-invitation-previews/v1").contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var session = request.getSession(false);
        if (session != null && session.getAttribute(AuthenticationSessionService.IDENTITY) instanceof ZitadelClient.ProviderSession identity) {
            try {
                if (!provider.isValid(identity)) throw new StaffAccessDeniedException("Provider session is invalid");
                var account = accounts.findByProviderSubject(identity.userId())
                    .orElseThrow(() -> new StaffAccessDeniedException("Account is unavailable"));
                var signedIn = (Instant) session.getAttribute(AuthenticationSessionService.SIGNED_IN_AT);
                if (signedIn == null || (account.getAuthenticationCutoff() != null
                    && !signedIn.isAfter(account.getAuthenticationCutoff())))
                    throw new StaffAccessDeniedException("Session was revoked");
                var principal = new StaffPrincipal(account.getId(), account.getTenantId(), account.getTenantRole());
                access.resolve(principal);
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_TENANT_ACCOUNT"))));
                SecurityContextHolder.setContext(context);
                cookies.setSessionId(request, response, session.getId());
            } catch (StaffAccessDeniedException exception) {
                session.invalidate();
                cookies.expireSession(request, response);
                errors.commence(request, response, new org.springframework.security.authentication.BadCredentialsException("Invalid session"));
                return;
            } catch (ResponseStatusException exception) {
                errors.unavailable(response);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
