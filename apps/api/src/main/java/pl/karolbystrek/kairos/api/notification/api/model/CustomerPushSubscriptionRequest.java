package pl.karolbystrek.kairos.api.notification.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.karolbystrek.kairos.api.notification.application.model.CustomerPushSubscriptionInput;

import java.time.Instant;

public record CustomerPushSubscriptionRequest(
        @NotBlank @Size(max = 2048) String endpoint,
        Instant expirationTime,
        @Valid @NotNull PushSubscriptionKeysRequest keys
) {

    public CustomerPushSubscriptionInput toInput() {
        return new CustomerPushSubscriptionInput(
                endpoint,
                keys.p256dh(),
                keys.auth(),
                expirationTime
        );
    }
}
