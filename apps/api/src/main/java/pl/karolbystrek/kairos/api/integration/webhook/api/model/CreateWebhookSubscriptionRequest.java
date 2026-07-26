package pl.karolbystrek.kairos.api.integration.webhook.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;

import java.util.Set;
import java.util.UUID;

public record CreateWebhookSubscriptionRequest(
        @NotNull UUID integrationId,
        @NotBlank
        @CodePointLength(max = 64, message = "Name must contain at most 64 characters")
        String name,
        @NotBlank @Size(max = 2048) String destinationUrl,
        @NotEmpty Set<@NotNull UUID> locationIds,
        @NotEmpty Set<@NotBlank String> eventTypes
) {
}
