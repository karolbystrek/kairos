package pl.karolbystrek.kairos.api.integration.webhook.domain;

public enum WebhookDeliveryStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    DEAD_LETTERED
}
