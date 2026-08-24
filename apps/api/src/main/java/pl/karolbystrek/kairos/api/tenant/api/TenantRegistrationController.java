package pl.karolbystrek.kairos.api.tenant.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.application.model.PanelPrincipal;
import pl.karolbystrek.kairos.api.authentication.api.model.CurrentAccountResponse;
import pl.karolbystrek.kairos.api.authentication.application.CurrentAccountService;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.AuthenticationCookieService;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.CsrfTokenService;
import pl.karolbystrek.kairos.api.tenant.api.model.TenantRegistrationRequest;
import pl.karolbystrek.kairos.api.tenant.application.TenantRegistrationService;

@RestController
@RequestMapping("/tenant-registrations/v1")
@RequiredArgsConstructor
class TenantRegistrationController {

    private final TenantRegistrationService tenantRegistrationService;
    private final CurrentAccountService currentAccountService;
    private final AuthenticationCookieService cookieService;
    private final CsrfTokenService csrfTokenService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CurrentAccountResponse register(
        @AuthenticationPrincipal PanelPrincipal signedInAccount,
        @Valid @RequestBody TenantRegistrationRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        var session = tenantRegistrationService.register(
            signedInAccount,
            request.token(),
            request.username(),
            request.email(),
            request.password()
        );
        var account = CurrentAccountResponse.from(currentAccountService.get(session.principal()));
        cookieService.write(servletResponse, session);
        csrfTokenService.rotate(servletRequest, servletResponse);
        return account;
    }
}
