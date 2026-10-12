package pl.karolbystrek.kairos.api.account.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.api.model.AccountInvitationResponse;
import pl.karolbystrek.kairos.api.account.api.model.CreateAccountInvitationRequest;
import pl.karolbystrek.kairos.api.account.api.model.AccountInvitationPageResponse;
import pl.karolbystrek.kairos.api.account.api.model.ResendAccountInvitationRequest;
import org.springframework.web.bind.annotation.RequestParam;
import pl.karolbystrek.kairos.api.account.application.AccountInvitationService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;

import java.util.UUID;

@RestController
@RequestMapping("/account-invitations/v1")
@RequiredArgsConstructor
class AccountInvitationController {

    private final AccountInvitationService invitationService;

    @GetMapping
    AccountInvitationPageResponse list(@AuthenticationPrincipal StaffPrincipal principal,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "desc") String sort) {
        return AccountInvitationPageResponse.from(invitationService.history(principal, page, size, sort));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountInvitationResponse create(@AuthenticationPrincipal StaffPrincipal principal,
        @Valid @RequestBody CreateAccountInvitationRequest request) {
        return AccountInvitationResponse.from(invitationService.create(principal, request.locationId(), request.role(),
            request.email(), Boolean.TRUE.equals(request.replaceExisting())).invitation());
    }

    @PostMapping("/{invitationId}/resend")
    @ResponseStatus(HttpStatus.CREATED)
    AccountInvitationResponse resend(@AuthenticationPrincipal StaffPrincipal principal, @PathVariable UUID invitationId,
        @Valid @RequestBody ResendAccountInvitationRequest request) {
        return AccountInvitationResponse.from(invitationService.resend(principal, invitationId, Boolean.TRUE.equals(request.replaceExisting())).invitation());
    }

    @DeleteMapping("/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(
        @AuthenticationPrincipal StaffPrincipal principal,
        @PathVariable UUID invitationId
    ) {
        invitationService.revoke(principal, invitationId);
    }
}
