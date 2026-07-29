package pl.karolbystrek.kairos.api.notification.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ReplaceCustomerPushSubscriptionRequest(
        @Valid @NotNull CustomerPushSubscriptionRequest previousSubscription,
        @Valid @NotNull CustomerPushSubscriptionRequest currentSubscription,
        @NotNull @Size(max = 100) List<UUID> trackingReferences
) {
}
