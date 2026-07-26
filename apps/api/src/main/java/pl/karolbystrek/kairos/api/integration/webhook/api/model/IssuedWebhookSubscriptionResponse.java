package pl.karolbystrek.kairos.api.integration.webhook.api.model;

import pl.karolbystrek.kairos.api.integration.webhook.application.model.IssuedWebhookSubscriptionView;

public record IssuedWebhookSubscriptionResponse(
        WebhookSubscriptionResponse subscription,
        String signingSecret
) {

    public static IssuedWebhookSubscriptionResponse from(IssuedWebhookSubscriptionView view) {
        return new IssuedWebhookSubscriptionResponse(
                WebhookSubscriptionResponse.from(view.subscription()),
                view.signingSecret()
        );
    }
}
