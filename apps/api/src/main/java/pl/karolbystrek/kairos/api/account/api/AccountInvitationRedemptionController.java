package pl.karolbystrek.kairos.api.account.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.api.model.RedeemAccountInvitationRequest;
import pl.karolbystrek.kairos.api.account.application.AccountInvitationService;
import pl.karolbystrek.kairos.api.account.application.model.PanelPrincipal;
import pl.karolbystrek.kairos.api.authentication.api.model.CurrentAccountResponse;
import pl.karolbystrek.kairos.api.authentication.application.AuthenticationSessionService;
import pl.karolbystrek.kairos.api.authentication.application.CurrentAccountService;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.AuthenticationCookieService;
import pl.karolbystrek.kairos.api.authentication.infrastructure.web.CsrfTokenService;

@RestController
@RequestMapping("/account-invitation-redemptions/v1")
@RequiredArgsConstructor
class AccountInvitationRedemptionController {

    private final AccountInvitationService invitationService;
    private final AuthenticationSessionService sessionService;
    private final CurrentAccountService currentAccountService;
    private final AuthenticationCookieService cookieService;
    private final CsrfTokenService csrfTokenService;

    @PostMapping
    CurrentAccountResponse redeem(
        @AuthenticationPrincipal PanelPrincipal signedInAccount,
        @Valid @RequestBody RedeemAccountInvitationRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        var principal = invitationService.redeem(
            signedInAccount,
            request.token(),
            request.username(),
            request.email(),
            request.password()
        );
        var session = sessionService.start(principal);
        var account = CurrentAccountResponse.from(currentAccountService.get(principal));
        cookieService.write(servletResponse, session);
        csrfTokenService.rotate(servletRequest, servletResponse);
        return account;
    }
}
