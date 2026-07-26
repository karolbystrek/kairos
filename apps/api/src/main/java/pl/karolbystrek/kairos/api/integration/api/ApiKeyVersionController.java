package pl.karolbystrek.kairos.api.integration.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.integration.api.model.ApiKeyVersionResponse;
import pl.karolbystrek.kairos.api.integration.api.model.IssuedApiKeyVersionResponse;
import pl.karolbystrek.kairos.api.integration.api.model.RotateApiKeyVersionRequest;
import pl.karolbystrek.kairos.api.integration.application.ApiKeyManagementService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api-key-versions/v1")
@RequiredArgsConstructor
class ApiKeyVersionController {

    private final ApiKeyManagementService apiKeyService;

    @GetMapping
    List<ApiKeyVersionResponse> list(
            @AuthenticationPrincipal StaffPrincipal principal,
            @RequestParam UUID apiKeyId
    ) {
        var versions = apiKeyService.listVersions(principal, apiKeyId);
        return versions.stream()
                .map(ApiKeyVersionResponse::from)
                .toList();
    }

    @PostMapping
    ResponseEntity<IssuedApiKeyVersionResponse> rotate(
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody RotateApiKeyVersionRequest request
    ) {
        var issued = apiKeyService.rotate(principal, request.apiKeyId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(IssuedApiKeyVersionResponse.from(issued));
    }
}
