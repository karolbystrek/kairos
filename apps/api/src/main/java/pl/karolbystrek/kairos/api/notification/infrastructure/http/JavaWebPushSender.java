package pl.karolbystrek.kairos.api.notification.infrastructure.http;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.network.PinnedHttpExecutor;
import pl.karolbystrek.kairos.api.notification.application.model.WebPushMessage;
import pl.karolbystrek.kairos.api.notification.application.model.WebPushResult;
import pl.karolbystrek.kairos.api.notification.application.port.WebPushSender;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;
import pl.karolbystrek.kairos.api.notification.infrastructure.security.VapidKeyMaterial;
import pl.karolbystrek.kairos.api.notification.infrastructure.security.WebPushPayloadEncryptor;
import pl.karolbystrek.kairos.api.util.Hashing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JavaWebPushSender implements WebPushSender {

    private static final String TIMEOUT_DETAIL = "Web Push submission exceeded the total timeout";
    private static final Duration VAPID_TOKEN_LIFETIME = Duration.ofHours(12);

    private final PushDestinationPolicy destinationPolicy;
    private final WebPushPayloadEncryptor payloadEncryptor;
    private final VapidKeyMaterial vapidKeyMaterial;
    private final CustomerNotificationProperties properties;
    private final Clock clock;

    @Override
    public WebPushResult send(WebPushMessage message) {
        var now = clock.instant();
        var remaining = Duration.between(now, message.deadline());
        if (!remaining.isPositive()) {
            return failure("EXPIRED", "Customer Push freshness deadline has passed");
        }
        var encrypted = payloadEncryptor.encrypt(
                message.payload(),
                message.p256dhKey(),
                message.authSecret()
        );
        return PinnedHttpExecutor.run(
                properties.delivery().totalTimeout(),
                "Web Push submission",
                JavaWebPushSender::failure,
                execution -> execute(message, encrypted, Math.max(1, remaining.toSeconds()), execution)
        );
    }

    private WebPushResult execute(
            WebPushMessage message,
            byte[] encrypted,
            long ttlSeconds,
            PinnedHttpExecutor.Execution execution
    ) throws IOException {
        var destination = destinationPolicy.requireAllowedAndResolve(message.endpoint());
        if (execution.expired()) {
            return failure("TIMEOUT", TIMEOUT_DETAIL);
        }
        var client = execution.open(
                destination.uri().getHost(),
                destination.addresses(),
                properties.delivery().totalTimeout()
        );
        if (client == null) {
            return failure("TIMEOUT", TIMEOUT_DETAIL);
        }

        var request = new HttpPost(destination.uri());
        request.setHeader("Authorization", createAuthorization(PushDestinationPolicy.origin(destination.uri())));
        request.setHeader("Content-Encoding", "aes128gcm");
        request.setHeader("TTL", Long.toString(ttlSeconds));
        request.setHeader("Urgency", "high");
        request.setHeader("Topic", topic(message.orderId()));
        request.setEntity(new ByteArrayEntity(encrypted, ContentType.APPLICATION_OCTET_STREAM));
        try (client) {
            return client.execute(request, response -> {
                var retryAfter = parseRetryAfter(response.getFirstHeader("Retry-After"), clock.instant());
                EntityUtils.consumeQuietly(response.getEntity());
                var status = response.getCode();
                return new WebPushResult(
                        status,
                        retryAfter,
                        status >= 200 && status < 300 ? "ACCEPTED" : "HTTP_" + status,
                        status >= 200 && status < 300
                                ? null
                                : "Push service returned HTTP " + status
                );
            });
        } finally {
            execution.release(client);
        }
    }

    private String createAuthorization(String audience) {
        try {
            var now = clock.instant();
            var claims = new JWTClaimsSet.Builder()
                    .audience(audience)
                    .subject(properties.vapid().subject())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(VAPID_TOKEN_LIFETIME)))
                    .build();
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), claims);
            jwt.sign(new ECDSASigner(vapidKeyMaterial.privateKey()));
            return "vapid t=" + jwt.serialize()
                    + ", k=" + vapidKeyMaterial.applicationServerKeyBase64();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Could not sign a VAPID authorization token", exception);
        }
    }

    private static Instant parseRetryAfter(Header header, Instant now) {
        if (header == null) {
            return null;
        }
        var value = header.getValue().strip();
        try {
            var seconds = Long.parseLong(value);
            return seconds < 0 ? null : now.plusSeconds(seconds);
        } catch (NumberFormatException ignored) {
            try {
                return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            } catch (DateTimeParseException invalidDate) {
                return null;
            }
        }
    }

    private static String topic(UUID orderId) {
        var digest = Hashing.sha256(orderId.toString().getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 32);
    }

    private static WebPushResult failure(String outcome, String diagnostic) {
        return new WebPushResult(null, null, outcome, diagnostic);
    }
}
