package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http;

import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config.WebhookProperties;
import pl.karolbystrek.kairos.api.network.InvalidDestinationException;
import pl.karolbystrek.kairos.api.network.PinnedHttpExecutor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class WebhookHttpClient {

    private static final String CONTENT_TYPE = "application/cloudevents+json";
    private static final String TIMEOUT_DETAIL = "Webhook delivery exceeded the total timeout";

    private final WebhookDestinationPolicy destinationPolicy;
    private final WebhookProperties properties;

    public WebhookHttpResult post(String destination, String body, String signatureHeader) {
        return PinnedHttpExecutor.run(
                properties.delivery().totalTimeout(),
                "Webhook delivery",
                WebhookHttpClient::failure,
                execution -> execute(destination, body, signatureHeader, execution)
        );
    }

    private WebhookHttpResult execute(
            String destination,
            String body,
            String signatureHeader,
            PinnedHttpExecutor.Execution execution
    ) throws IOException {
        WebhookDestinationPolicy.AllowedDestination allowedDestination;
        try {
            allowedDestination = destinationPolicy.requireAllowedAndResolve(destination);
        } catch (InvalidDestinationException exception) {
            return failure("DESTINATION_BLOCKED", PinnedHttpExecutor.safeMessage(exception, "Webhook delivery failed"));
        }
        if (execution.expired()) {
            return failure("TIMEOUT", TIMEOUT_DETAIL);
        }

        var client = execution.open(
                allowedDestination.uri().getHost(),
                allowedDestination.addresses(),
                properties.delivery().totalTimeout()
        );
        if (client == null) {
            return failure("TIMEOUT", TIMEOUT_DETAIL);
        }

        var request = new HttpPost(allowedDestination.uri());
        request.setHeader("Kairos-Signature", signatureHeader);
        request.setEntity(new ByteArrayEntity(
                body.getBytes(StandardCharsets.UTF_8),
                ContentType.create(CONTENT_TYPE)
        ));
        try (client) {
            return client.execute(request, response -> {
                var entity = response.getEntity();
                if (entity == null) {
                    return responseResult(response.getCode(), new byte[0], false);
                }
                try (var responseBody = entity.getContent()) {
                    var maximumBytes = properties.delivery().maximumResponseBodyBytes();
                    var bytes = responseBody.readNBytes(maximumBytes + 1);
                    var truncated = bytes.length > maximumBytes;
                    var retained = truncated ? Arrays.copyOf(bytes, maximumBytes) : bytes;
                    return responseResult(response.getCode(), retained, truncated);
                }
            });
        } finally {
            execution.release(client);
        }
    }

    private static WebhookHttpResult responseResult(
            int statusCode,
            byte[] body,
            boolean truncated
    ) {
        var errorType = statusCode >= 200 && statusCode < 300
                ? null
                : "NON_2XX_RESPONSE";
        return new WebhookHttpResult(
                statusCode,
                new String(body, StandardCharsets.UTF_8),
                truncated,
                errorType,
                errorType == null ? null : "Webhook recipient returned a non-2xx response"
        );
    }

    private static WebhookHttpResult failure(String errorType, String detail) {
        return new WebhookHttpResult(null, null, false, errorType, detail);
    }
}
