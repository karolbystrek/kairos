package pl.karolbystrek.kairos.api.account.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.api.model.CreateAccountInvitationRequest;
import pl.karolbystrek.kairos.api.account.api.model.AccountInvitationResponse;
import pl.karolbystrek.kairos.api.account.api.model.CreatedAccountInvitationResponse;
import pl.karolbystrek.kairos.api.account.application.AccountInvitationService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.config.ApplicationOriginsProperties;

@RestController
@RequestMapping("/account-invitations/v1")
@RequiredArgsConstructor
class AccountInvitationController {

    private final AccountInvitationService invitationService;
    private final ApplicationOriginsProperties origins;

    @GetMapping
    java.util.List<AccountInvitationResponse> list(
        @AuthenticationPrincipal StaffPrincipal principal
    ) {
        return invitationService.listPending(principal).stream()
            .map(AccountInvitationResponse::from)
            .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreatedAccountInvitationResponse create(
        @AuthenticationPrincipal StaffPrincipal principal,
        @Valid @RequestBody CreateAccountInvitationRequest request
    ) {
        return CreatedAccountInvitationResponse.from(
            invitationService.create(principal, request.locationId(), request.role()),
            origins.panel()
        );
    }

    @DeleteMapping("/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(
        @AuthenticationPrincipal StaffPrincipal principal,
        @PathVariable java.util.UUID invitationId
    ) {
        invitationService.revoke(principal, invitationId);
    }
}
