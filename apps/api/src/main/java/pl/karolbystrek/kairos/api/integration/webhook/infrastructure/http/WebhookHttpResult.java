package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http;

public record WebhookHttpResult(
        Integer statusCode,
        String responseBody,
        boolean responseTruncated,
        String errorType,
        String errorDetail
) {

    public boolean isSuccessful() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }
}
