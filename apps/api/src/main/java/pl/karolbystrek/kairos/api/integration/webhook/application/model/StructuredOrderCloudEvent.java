package pl.karolbystrek.kairos.api.integration.webhook.application.model;

import lombok.NonNull;

import java.time.Instant;
import java.util.UUID;

public record StructuredOrderCloudEvent(
        @NonNull String specversion,
        @NonNull UUID id,
        @NonNull String source,
        @NonNull String type,
        @NonNull String subject,
        @NonNull Instant time,
        @NonNull String datacontenttype,
        @NonNull ExternalOrderSnapshot data
) {
}
