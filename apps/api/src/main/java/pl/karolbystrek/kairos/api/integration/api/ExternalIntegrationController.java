package pl.karolbystrek.kairos.api.integration.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.integration.api.model.CreateExternalIntegrationRequest;
import pl.karolbystrek.kairos.api.integration.api.model.ExternalIntegrationResponse;
import pl.karolbystrek.kairos.api.integration.api.model.RenameExternalIntegrationRequest;
import pl.karolbystrek.kairos.api.integration.api.model.UpdateExternalIntegrationStatusRequest;
import pl.karolbystrek.kairos.api.integration.application.ExternalIntegrationManagementService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/external-integrations/v1")
@RequiredArgsConstructor
class ExternalIntegrationController {

    private final ExternalIntegrationManagementService integrationService;

    @GetMapping
    List<ExternalIntegrationResponse> list(@AuthenticationPrincipal StaffPrincipal principal) {
        var integrations = integrationService.list(principal);
        return integrations.stream()
                .map(ExternalIntegrationResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ExternalIntegrationResponse create(
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody CreateExternalIntegrationRequest request
    ) {
        var integration = integrationService.create(principal, request.name());
        return ExternalIntegrationResponse.from(integration);
    }

    @PutMapping("/{integrationId}")
    ExternalIntegrationResponse rename(
            @AuthenticationPrincipal StaffPrincipal principal,
            @PathVariable UUID integrationId,
            @Valid @RequestBody RenameExternalIntegrationRequest request
    ) {
        var integration = integrationService.rename(principal, integrationId, request.name());
        return ExternalIntegrationResponse.from(integration);
    }

    @PutMapping("/{integrationId}/status")
    ExternalIntegrationResponse updateStatus(
            @AuthenticationPrincipal StaffPrincipal principal,
            @PathVariable UUID integrationId,
            @Valid @RequestBody UpdateExternalIntegrationStatusRequest request
    ) {
        var integration = integrationService.changeStatus(principal, integrationId, request.status());
        return ExternalIntegrationResponse.from(integration);
    }

    @DeleteMapping("/{integrationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(
            @AuthenticationPrincipal StaffPrincipal principal,
            @PathVariable UUID integrationId
    ) {
        integrationService.archive(principal, integrationId);
    }
}
