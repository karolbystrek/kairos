package pl.karolbystrek.kairos.api.authentication.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.account.application.AccountCreationService;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.PanelPrincipal;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.authentication.api.model.*;
import pl.karolbystrek.kairos.api.authentication.application.*;
import pl.karolbystrek.kairos.api.authentication.application.exception.InvalidLoginException;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.CsrfTokenService;
import pl.karolbystrek.kairos.api.authentication.infrastructure.persistence.StaffIdentityStore;

import java.time.Clock;
import java.util.Locale;

@RestController
@RequiredArgsConstructor
public class AuthenticationController {
    private final StaffIdentityStore identities;
    private final OnboardingService onboarding;
    private final RegistrationVerificationService verification;
    private final AccountCreationService accounts;
    private final AuthenticationSessionService sessions;
    private final CurrentAccountService current;
    private final StaffAuthenticationService authentication;
    private final Clock clock;
    private final CsrfTokenService csrf;
    private final PasswordResetService passwordResets;

    @GetMapping("/auth/v1/csrf")
    CsrfTokenResponse csrf(HttpServletRequest request) { return new CsrfTokenResponse(csrf.current(request).getToken()); }

    @PostMapping("/auth/v1/login")
    Object login(@Valid @RequestBody LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        var email = normalize(input.email());
        var subject = identities.signIn(email, input.password());
        StaffPrincipal principal;
        try { principal = authentication.authenticate(subject, clock.instant()); }
        catch (StaffAccessDeniedException exception) {
            if (accounts.identityConflict(email, subject)) throw new InvalidLoginException();
            return begin(verification.resume(subject, email), request, response);
        }
        return finish(principal, subject, request, response);
    }

    @PostMapping("/tenant-registrations/v1")
    Object registerTenant(@Valid @RequestBody RegistrationRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        return register(input, false, request, response);
    }

    @PostMapping("/account-invitation-redemptions/v1")
    CurrentAccountResponse redeemInvitation(@Valid @RequestBody RegistrationRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        return (CurrentAccountResponse) register(input, true, request, response);
    }

    private Object register(RegistrationRequest input, boolean invited,
            HttpServletRequest request, HttpServletResponse response) {
        var existing = request.getSession(false);
        if ((existing != null && existing.getAttribute(AuthenticationSessionService.IDENTITY) != null)
            || (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof PanelPrincipal))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sign out before registering");
        if (invited && (input.token() == null || input.token().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is required");
        if (!invited) return begin(verification.register(normalize(input.email()), input.password()), request, response);
        var registration = onboarding.register(normalize(input.email()), input.password(), input.token());
        return finish(registration.principal(), registration.subject(), request, response);
    }

    private Object begin(RegistrationVerificationService.Pending pending,
            HttpServletRequest request, HttpServletResponse response) {
        if (!verification.verificationRequired())
            return finish(verification.verify(pending, null), pending.subject(), request, response);
        var old = request.getSession(false);
        if (old != null) old.invalidate();
        request.getSession(true).setAttribute("kairos.pendingRegistration", pending);
        csrf.rotate(request, response);
        return verification.state(pending);
    }

    @GetMapping("/auth/v1/verification")
    RegistrationVerificationService.State verificationState(HttpServletRequest request) {
        return verification.state(pending(request));
    }

    record VerifyCodeRequest(@Pattern(regexp = "[0-9]{6}", message = "Enter the 6-digit code.") String code) {}

    @PostMapping("/auth/v1/verification")
    CurrentAccountResponse verify(@Valid @RequestBody VerifyCodeRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        if (input.code() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the 6-digit code.");
        var pending = pending(request);
        return finish(verification.verify(pending, input.code()), pending.subject(), request, response);
    }

    @PostMapping("/auth/v1/verification/resend")
    RegistrationVerificationService.State resend(HttpServletRequest request) {
        return verification.resend(pending(request));
    }

    private RegistrationVerificationService.Pending pending(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute("kairos.pendingRegistration") instanceof RegistrationVerificationService.Pending pending))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue registration.");
        return pending;
    }

    private CurrentAccountResponse finish(StaffPrincipal principal, String subject,
            HttpServletRequest request, HttpServletResponse response) {
        var result = CurrentAccountResponse.from(current.get(principal));
        sessions.establish(request, principal, subject);
        csrf.rotate(request, response);
        return result;
    }

    @PostMapping("/auth/v1/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(request);
        csrf.rotate(request, response);
    }

    @PostMapping("/auth/v1/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal PanelPrincipal principal, @Valid @RequestBody ChangePasswordRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        identities.changePassword(current.providerSubject(principal), input.currentPassword(), input.password());
        sessions.logoutAll(principal);
        sessions.logout(request);
        csrf.rotate(request, response);
    }

    @PostMapping("/auth/v1/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestPasswordReset(@Valid @RequestBody PasswordResetRequest input) {
        passwordResets.request(normalize(input.email()));
    }

    @PostMapping("/auth/v1/password-reset/confirmation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody PasswordResetConfirmationRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        passwordResets.reset(normalize(input.email()), input.code(), input.password());
        sessions.logout(request);
        csrf.rotate(request, response);
    }

    @GetMapping("/auth/v1/me")
    CurrentAccountResponse me(@AuthenticationPrincipal PanelPrincipal principal) {
        return CurrentAccountResponse.from(current.get(principal));
    }
    private static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }
}
