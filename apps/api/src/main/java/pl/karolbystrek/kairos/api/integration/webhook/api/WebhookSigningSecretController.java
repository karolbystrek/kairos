package pl.karolbystrek.kairos.api.integration.webhook.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.integration.webhook.api.model.IssuedWebhookSigningSecretResponse;
import pl.karolbystrek.kairos.api.integration.webhook.api.model.RetireWebhookSigningSecretRequest;
import pl.karolbystrek.kairos.api.integration.webhook.api.model.RotateWebhookSigningSecretRequest;
import pl.karolbystrek.kairos.api.integration.webhook.api.model.WebhookSigningSecretVersionResponse;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookSubscriptionManagementService;

import java.util.UUID;

@RestController
@RequestMapping("/webhook-signing-secrets/v1")
@RequiredArgsConstructor
class WebhookSigningSecretController {

    private final WebhookSubscriptionManagementService subscriptionService;

    @PostMapping
    ResponseEntity<IssuedWebhookSigningSecretResponse> rotate(
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody RotateWebhookSigningSecretRequest request
    ) {
        var issued = subscriptionService.rotateSigningSecret(principal, request.subscriptionId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(IssuedWebhookSigningSecretResponse.from(issued));
    }

    @PutMapping("/{versionId}/retirement")
    WebhookSigningSecretVersionResponse retire(
            @AuthenticationPrincipal StaffPrincipal principal,
            @PathVariable UUID versionId,
            @Valid @RequestBody RetireWebhookSigningSecretRequest request
    ) {
        var version = subscriptionService.retireSigningSecret(
                principal,
                request.subscriptionId(),
                versionId
        );
        return WebhookSigningSecretVersionResponse.from(version);
    }
}
