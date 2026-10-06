package pl.karolbystrek.kairos.api.authentication.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.model.PanelPrincipal;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.authentication.api.model.*;
import pl.karolbystrek.kairos.api.authentication.application.*;
import pl.karolbystrek.kairos.api.authentication.application.exception.InvalidLoginException;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.CsrfTokenService;

import java.util.Locale;

@RestController
@RequiredArgsConstructor
public class AuthenticationController {
    private final ZitadelClient provider;
    private final OnboardingService onboarding;
    private final AuthenticationSessionService sessions;
    private final CurrentAccountService current;
    private final AccountRepository accounts;
    private final StaffAccessService access;
    private final CsrfTokenService csrf;

    @GetMapping("/auth/v1/csrf")
    CsrfTokenResponse csrf(HttpServletRequest request) { return new CsrfTokenResponse(csrf.current(request).getToken()); }

    @PostMapping("/auth/v1/login")
    CurrentAccountResponse login(@Valid @RequestBody LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        var identity = provider.signIn(normalize(input.email()), input.password());
        try {
            var account = accounts.findByProviderSubject(identity.userId()).orElseThrow(InvalidLoginException::new);
            var principal = new StaffPrincipal(account.getId(), account.getTenantId(), account.getTenantRole());
            access.resolve(principal);
            return finish(principal, identity, request, response);
        } catch (RuntimeException exception) {
            provider.terminate(identity);
            throw exception;
        }
    }

    @PostMapping("/tenant-registrations/v1")
    CurrentAccountResponse registerTenant(@Valid @RequestBody RegistrationRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        return register(input, false, request, response);
    }

    @PostMapping("/account-invitation-redemptions/v1")
    CurrentAccountResponse redeemInvitation(@Valid @RequestBody RegistrationRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        return register(input, true, request, response);
    }

    private CurrentAccountResponse register(RegistrationRequest input, boolean invited,
            HttpServletRequest request, HttpServletResponse response) {
        var existing = request.getSession(false);
        if ((existing != null && existing.getAttribute(AuthenticationSessionService.IDENTITY) != null)
            || (org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null
                && org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof PanelPrincipal))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sign out before registering");
        if (invited && (input.token() == null || input.token().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is required");
        var registration = onboarding.register(normalize(input.email()), input.password(), invited ? input.token() : null);
        return finish(registration.principal(), registration.session(), request, response);
    }

    private CurrentAccountResponse finish(StaffPrincipal principal, ZitadelClient.ProviderSession identity,
            HttpServletRequest request, HttpServletResponse response) {
        var result = CurrentAccountResponse.from(current.get(principal));
        sessions.establish(request, principal, identity);
        csrf.rotate(request, response);
        return result;
    }

    @PostMapping("/auth/v1/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(request);
        csrf.rotate(request, response);
    }

    @PostMapping("/auth/v1/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logoutAll(@AuthenticationPrincipal PanelPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        sessions.logoutAll(principal);
        sessions.logout(request);
        csrf.rotate(request, response);
    }

    @PostMapping("/auth/v1/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal PanelPrincipal principal, @Valid @RequestBody ChangePasswordRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        var account = accounts.findById(principal.accountId()).orElseThrow(InvalidLoginException::new);
        provider.changePassword(account.getProviderSubject(), input.currentPassword(), input.password());
        sessions.logoutAll(principal);
        sessions.logout(request);
        csrf.rotate(request, response);
    }

    @GetMapping("/auth/v1/me")
    CurrentAccountResponse me(@AuthenticationPrincipal PanelPrincipal principal) {
        return CurrentAccountResponse.from(current.get(principal));
    }
    private static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }
}
