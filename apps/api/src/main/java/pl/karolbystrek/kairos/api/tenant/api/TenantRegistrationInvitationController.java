package pl.karolbystrek.kairos.api.tenant.api;

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
import pl.karolbystrek.kairos.api.account.application.model.PlatformOperatorPrincipal;
import pl.karolbystrek.kairos.api.config.ApplicationOriginsProperties;
import pl.karolbystrek.kairos.api.tenant.api.model.CreateTenantRegistrationInvitationRequest;
import pl.karolbystrek.kairos.api.tenant.api.model.CreatedTenantRegistrationInvitationResponse;
import pl.karolbystrek.kairos.api.tenant.api.model.TenantRegistrationInvitationResponse;
import pl.karolbystrek.kairos.api.tenant.application.TenantRegistrationInvitationService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tenant-registration-invitations/v1")
@RequiredArgsConstructor
class TenantRegistrationInvitationController {

    private final TenantRegistrationInvitationService invitationService;
    private final ApplicationOriginsProperties origins;

    @GetMapping
    List<TenantRegistrationInvitationResponse> list(
        @AuthenticationPrincipal PlatformOperatorPrincipal principal
    ) {
        return invitationService.listPending(principal).stream()
            .map(TenantRegistrationInvitationResponse::from)
            .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreatedTenantRegistrationInvitationResponse create(
        @AuthenticationPrincipal PlatformOperatorPrincipal principal,
        @Valid @RequestBody CreateTenantRegistrationInvitationRequest request
    ) {
        return CreatedTenantRegistrationInvitationResponse.from(
            invitationService.create(principal, request.label()),
            origins.panel()
        );
    }

    @DeleteMapping("/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(
        @AuthenticationPrincipal PlatformOperatorPrincipal principal,
        @PathVariable UUID invitationId
    ) {
        invitationService.revoke(principal, invitationId);
    }
}
