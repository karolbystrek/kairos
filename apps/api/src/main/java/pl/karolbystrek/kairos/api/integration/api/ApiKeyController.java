package pl.karolbystrek.kairos.api.integration.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.integration.api.model.ApiKeyResponse;
import pl.karolbystrek.kairos.api.integration.api.model.CreateApiKeyRequest;
import pl.karolbystrek.kairos.api.integration.api.model.IssuedApiKeyResponse;
import pl.karolbystrek.kairos.api.integration.application.ApiKeyManagementService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api-keys/v1")
@RequiredArgsConstructor
class ApiKeyController {

    private final ApiKeyManagementService apiKeyService;

    @GetMapping
    List<ApiKeyResponse> list(
            @AuthenticationPrincipal StaffPrincipal principal,
            @RequestParam UUID integrationId
    ) {
        var apiKeys = apiKeyService.list(principal, integrationId);
        return apiKeys.stream()
                .map(ApiKeyResponse::from)
                .toList();
    }

    @PostMapping
    ResponseEntity<IssuedApiKeyResponse> issue(
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        var issued = apiKeyService.issue(
                principal,
                request.integrationId(),
                request.name(),
                request.scopes(),
                request.locationIds(),
                request.expiresAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(IssuedApiKeyResponse.from(issued));
    }

    @PutMapping("/{apiKeyId}/revocation")
    ApiKeyResponse revoke(
            @AuthenticationPrincipal StaffPrincipal principal,
            @PathVariable UUID apiKeyId
    ) {
        var apiKey = apiKeyService.revoke(principal, apiKeyId);
        return ApiKeyResponse.from(apiKey);
    }
}
