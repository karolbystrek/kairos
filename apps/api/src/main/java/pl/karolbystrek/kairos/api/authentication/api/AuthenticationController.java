package pl.karolbystrek.kairos.api.authentication.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.authentication.api.model.CsrfTokenResponse;
import pl.karolbystrek.kairos.api.authentication.api.model.CurrentAccountResponse;
import pl.karolbystrek.kairos.api.authentication.api.model.LoginRequest;
import pl.karolbystrek.kairos.api.authentication.application.AuthenticationSessionService;
import pl.karolbystrek.kairos.api.authentication.application.CurrentAccountService;
import pl.karolbystrek.kairos.api.authentication.application.LocalAuthenticationService;
import pl.karolbystrek.kairos.api.authentication.application.exception.InvalidRefreshCredentialException;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.AuthenticationCookieService;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.CsrfTokenService;

import static pl.karolbystrek.kairos.api.authentication.infrastructure.web.AuthenticationHttpNames.CSRF_COOKIE;
import static pl.karolbystrek.kairos.api.authentication.infrastructure.web.AuthenticationHttpNames.CSRF_HEADER;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
class AuthenticationController {

    private final LocalAuthenticationService localAuthenticationService;
    private final AuthenticationSessionService sessionService;
    private final CurrentAccountService currentAccountService;
    private final AuthenticationCookieService cookieService;
    private final CsrfTokenService csrfTokenService;

    @GetMapping("/csrf")
    CsrfTokenResponse csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return new CsrfTokenResponse(
            CSRF_COOKIE,
            CSRF_HEADER
        );
    }

    @PostMapping("/login")
    CurrentAccountResponse login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        var session = localAuthenticationService.authenticate(
            request.username(),
            request.password()
        );
        var account = currentAccountService.get(session.principal());
        var currentAccount = CurrentAccountResponse.from(account);
        cookieService.write(servletResponse, session);
        csrfTokenService.rotate(servletRequest, servletResponse);
        return currentAccount;
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void refresh(HttpServletRequest request, HttpServletResponse response) {
        var refreshCredential = cookieService.readRefreshCredential(request);
        try {
            var session = sessionService.rotate(refreshCredential);
            cookieService.write(response, session);
        }
        catch (InvalidRefreshCredentialException exception) {
            cookieService.clear(response);
            throw exception;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(
        @AuthenticationPrincipal StaffPrincipal principal,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        sessionService.logout(principal, cookieService.readRefreshCredential(request));
        cookieService.clear(response);
        csrfTokenService.rotate(request, response);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logoutAll(
        @AuthenticationPrincipal StaffPrincipal principal,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        sessionService.logoutAll(principal);
        cookieService.clear(response);
        csrfTokenService.rotate(request, response);
    }

    @GetMapping("/me")
    CurrentAccountResponse me(@AuthenticationPrincipal StaffPrincipal principal) {
        var account = currentAccountService.get(principal);
        return CurrentAccountResponse.from(account);
    }
}
