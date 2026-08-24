package pl.karolbystrek.kairos.api.tenant.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.tenant.api.model.TenantRegistrationInvitationPreviewResponse;
import pl.karolbystrek.kairos.api.tenant.api.model.TenantRegistrationInvitationTokenRequest;
import pl.karolbystrek.kairos.api.tenant.application.TenantRegistrationInvitationService;

@RestController
@RequestMapping("/tenant-registration-invitation-previews/v1")
@RequiredArgsConstructor
class TenantRegistrationInvitationPreviewController {

    private final TenantRegistrationInvitationService invitationService;

    @PostMapping
    TenantRegistrationInvitationPreviewResponse preview(
        @Valid @RequestBody TenantRegistrationInvitationTokenRequest request
    ) {
        return TenantRegistrationInvitationPreviewResponse.from(
            invitationService.preview(request.token())
        );
    }
}
