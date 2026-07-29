package pl.karolbystrek.kairos.api.notification.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushSubscriptionKeysRequest(
        @NotBlank @Size(max = 120) String p256dh,
        @NotBlank @Size(max = 64) String auth
) {
}
