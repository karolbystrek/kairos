package pl.karolbystrek.kairos.api.integration.webhook.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.integration.application.exception.InvalidIntegrationRequestException;
import pl.karolbystrek.kairos.api.integration.webhook.api.model.CreateWebhookSubscriptionRequest;
import pl.karolbystrek.kairos.api.integration.webhook.api.model.IssuedWebhookSubscriptionResponse;
import pl.karolbystrek.kairos.api.integration.webhook.api.model.UpdateWebhookSubscriptionRequest;
import pl.karolbystrek.kairos.api.integration.webhook.api.model.UpdateWebhookSubscriptionStatusRequest;
import pl.karolbystrek.kairos.api.integration.webhook.api.model.WebhookSubscriptionResponse;
import pl.karolbystrek.kairos.api.integration.webhook.application.WebhookSubscriptionManagementService;
import pl.karolbystrek.kairos.api.order.domain.OrderEventType;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/webhook-subscriptions/v1")
@RequiredArgsConstructor
class WebhookSubscriptionController {

    private final WebhookSubscriptionManagementService subscriptionService;

    @GetMapping
    List<WebhookSubscriptionResponse> list(
            @AuthenticationPrincipal StaffPrincipal principal,
            @RequestParam UUID integrationId
    ) {
        var subscriptions = subscriptionService.list(principal, integrationId);
        return subscriptions.stream()
                .map(WebhookSubscriptionResponse::from)
                .toList();
    }

    @PostMapping
    ResponseEntity<IssuedWebhookSubscriptionResponse> create(
            @AuthenticationPrincipal StaffPrincipal principal,
            @Valid @RequestBody CreateWebhookSubscriptionRequest request
    ) {
        var issued = subscriptionService.create(
                principal,
                request.integrationId(),
                request.name(),
                request.destinationUrl(),
                request.locationIds(),
                parseEventTypes(request.eventTypes())
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(IssuedWebhookSubscriptionResponse.from(issued));
    }

    @PutMapping("/{subscriptionId}")
    WebhookSubscriptionResponse update(
            @AuthenticationPrincipal StaffPrincipal principal,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody UpdateWebhookSubscriptionRequest request
    ) {
        var subscription = subscriptionService.update(
                principal,
                subscriptionId,
                request.name(),
                request.destinationUrl(),
                request.locationIds(),
                parseEventTypes(request.eventTypes())
        );
        return WebhookSubscriptionResponse.from(subscription);
    }

    @PutMapping("/{subscriptionId}/status")
    WebhookSubscriptionResponse updateStatus(
            @AuthenticationPrincipal StaffPrincipal principal,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody UpdateWebhookSubscriptionStatusRequest request
    ) {
        var subscription = subscriptionService.changeStatus(
                principal,
                subscriptionId,
                request.status()
        );
        return WebhookSubscriptionResponse.from(subscription);
    }

    @DeleteMapping("/{subscriptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(
            @AuthenticationPrincipal StaffPrincipal principal,
            @PathVariable UUID subscriptionId
    ) {
        subscriptionService.archive(principal, subscriptionId);
    }

    private static Set<OrderEventType> parseEventTypes(Set<String> eventTypes) {
        try {
            return eventTypes.stream()
                    .map(OrderEventType::fromCloudEventType)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException exception) {
            throw new InvalidIntegrationRequestException(exception.getMessage(), exception);
        }
    }
}
