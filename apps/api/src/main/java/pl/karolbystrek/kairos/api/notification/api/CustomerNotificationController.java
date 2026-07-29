package pl.karolbystrek.kairos.api.notification.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.karolbystrek.kairos.api.notification.api.model.CustomerNotificationConfigurationResponse;
import pl.karolbystrek.kairos.api.notification.api.model.CustomerPushSubscriptionRequest;
import pl.karolbystrek.kairos.api.notification.api.model.ReconcileCustomerPushSubscriptionRequest;
import pl.karolbystrek.kairos.api.notification.api.model.RemoveCustomerPushEnrollmentsRequest;
import pl.karolbystrek.kairos.api.notification.api.model.ReplaceCustomerPushSubscriptionRequest;
import pl.karolbystrek.kairos.api.notification.application.CustomerPushSubscriptionService;
import pl.karolbystrek.kairos.api.notification.infrastructure.security.VapidKeyMaterial;

@RestController
@RequestMapping("/customer-notifications/v1")
@RequiredArgsConstructor
class CustomerNotificationController {

    private final CustomerPushSubscriptionService subscriptionService;
    private final VapidKeyMaterial vapidKeyMaterial;

    @GetMapping("/configuration")
    CustomerNotificationConfigurationResponse configuration() {
        return new CustomerNotificationConfigurationResponse(
                vapidKeyMaterial.applicationServerKeyBase64()
        );
    }

    @PutMapping("/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reconcile(@Valid @RequestBody ReconcileCustomerPushSubscriptionRequest request) {
        subscriptionService.reconcile(
                request.subscription().toInput(),
                request.trackingReferences()
        );
    }

    @PostMapping("/subscription-replacement")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void replace(@Valid @RequestBody ReplaceCustomerPushSubscriptionRequest request) {
        subscriptionService.replace(
                request.previousSubscription().toInput(),
                request.currentSubscription().toInput(),
                request.trackingReferences()
        );
    }

    @DeleteMapping("/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disable(@Valid @RequestBody CustomerPushSubscriptionRequest request) {
        subscriptionService.disable(request.toInput());
    }

    @DeleteMapping("/enrollments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeEnrollments(
            @Valid @RequestBody RemoveCustomerPushEnrollmentsRequest request
    ) {
        subscriptionService.removeEnrollments(
                request.subscription().toInput(),
                request.trackingReferences()
        );
    }
}
