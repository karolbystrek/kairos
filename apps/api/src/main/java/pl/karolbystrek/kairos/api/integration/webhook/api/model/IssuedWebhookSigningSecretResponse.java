package pl.karolbystrek.kairos.api.integration.webhook.api.model;

import pl.karolbystrek.kairos.api.integration.webhook.application.model.IssuedWebhookSigningSecretView;

public record IssuedWebhookSigningSecretResponse(
        WebhookSigningSecretVersionResponse version,
        String signingSecret
) {

    public static IssuedWebhookSigningSecretResponse from(IssuedWebhookSigningSecretView view) {
        return new IssuedWebhookSigningSecretResponse(
                WebhookSigningSecretVersionResponse.from(view.version()),
                view.signingSecret()
        );
    }
}
