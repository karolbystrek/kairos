package pl.karolbystrek.kairos.api.account.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.api.model.AccountInvitationPreviewResponse;
import pl.karolbystrek.kairos.api.account.api.model.AccountInvitationTokenRequest;
import pl.karolbystrek.kairos.api.account.application.AccountInvitationService;

@RestController
@RequestMapping("/account-invitation-previews/v1")
@RequiredArgsConstructor
class AccountInvitationPreviewController {

    private final AccountInvitationService invitationService;

    @PostMapping
    AccountInvitationPreviewResponse preview(
        @Valid @RequestBody AccountInvitationTokenRequest request
    ) {
        return AccountInvitationPreviewResponse.from(invitationService.preview(request.token()));
    }
}
