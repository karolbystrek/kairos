package pl.karolbystrek.kairos.api.notification.infrastructure.http;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.model.WebPushMessage;
import pl.karolbystrek.kairos.api.notification.application.model.WebPushResult;
import pl.karolbystrek.kairos.api.notification.application.port.WebPushSender;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;
import pl.karolbystrek.kairos.api.notification.infrastructure.security.VapidKeyMaterial;
import pl.karolbystrek.kairos.api.notification.infrastructure.security.WebPushPayloadEncryptor;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class JavaWebPushSender implements WebPushSender {

    private static final int MAXIMUM_RESPONSE_LINE_LENGTH = 8_192;
    private static final int MAXIMUM_RESPONSE_HEADER_COUNT = 100;
    private static final int MAXIMUM_LEADING_EMPTY_LINES = 10;
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
        var timeout = properties.delivery().totalTimeout();
        var deadlineExceeded = new AtomicBoolean();
        var clientReference = new AtomicReference<CloseableHttpClient>();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var future = executor.submit(() -> execute(
                    message,
                    encrypted,
                    Math.max(1, remaining.toSeconds()),
                    deadlineExceeded,
                    clientReference
            ));
            try {
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                deadlineExceeded.set(true);
                future.cancel(true);
                closeImmediately(clientReference.get());
                return failure("TIMEOUT", "Web Push submission exceeded the total timeout");
            } catch (ExecutionException exception) {
                var cause = exception.getCause();
                if (isTimeout(cause)) {
                    return failure("TIMEOUT", "Web Push submission exceeded the total timeout");
                }
                return failure("NETWORK_ERROR", safeMessage(cause));
            } catch (InterruptedException exception) {
                deadlineExceeded.set(true);
                future.cancel(true);
                closeImmediately(clientReference.get());
                Thread.currentThread().interrupt();
                return failure("INTERRUPTED", "Web Push submission was interrupted");
            }
        } finally {
            executor.shutdownNow();
            closeImmediately(clientReference.get());
        }
    }

    private WebPushResult execute(
            WebPushMessage message,
            byte[] encrypted,
            long ttlSeconds,
            AtomicBoolean deadlineExceeded,
            AtomicReference<CloseableHttpClient> clientReference
    ) throws IOException {
        var destination = destinationPolicy.requireAllowedAndResolve(message.endpoint());
        if (deadlineExceeded.get() || Thread.currentThread().isInterrupted()) {
            return failure("TIMEOUT", "Web Push submission exceeded the total timeout");
        }
        var client = buildClient(destination);
        clientReference.set(client);
        if (deadlineExceeded.get()) {
            closeImmediately(client);
            clientReference.compareAndSet(client, null);
            return failure("TIMEOUT", "Web Push submission exceeded the total timeout");
        }

        var request = new HttpPost(destination.uri());
        request.setHeader("Authorization", createAuthorization(destination.origin()));
        request.setHeader("Content-Encoding", "aes128gcm");
        request.setHeader("TTL", Long.toString(ttlSeconds));
        request.setHeader("Urgency", "high");
        request.setHeader("Topic", topic(message.eventId()));
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
            clientReference.compareAndSet(client, null);
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

    private CloseableHttpClient buildClient(
            PushDestinationPolicy.AllowedPushDestination destination
    ) {
        var timeout = Timeout.of(properties.delivery().totalTimeout());
        var connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(timeout)
                .setSocketTimeout(timeout)
                .build();
        var http1Config = Http1Config.custom()
                .setMaxLineLength(MAXIMUM_RESPONSE_LINE_LENGTH)
                .setMaxHeaderCount(MAXIMUM_RESPONSE_HEADER_COUNT)
                .setMaxEmptyLineCount(MAXIMUM_LEADING_EMPTY_LINES)
                .build();
        var connectionFactory = ManagedHttpClientConnectionFactory.builder()
                .http1Config(http1Config)
                .build();
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setConnectionFactory(connectionFactory)
                .setDnsResolver(new PinnedDnsResolver(
                        destination.uri().getHost(),
                        destination.addresses()
                ))
                .setDefaultConnectionConfig(connectionConfig)
                .setMaxConnTotal(1)
                .setMaxConnPerRoute(1)
                .build();
        var requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(timeout)
                .setResponseTimeout(timeout)
                .setRedirectsEnabled(false)
                .setAuthenticationEnabled(false)
                .setContentCompressionEnabled(false)
                .setProtocolUpgradeEnabled(false)
                .setHardCancellationEnabled(true)
                .build();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setConnectionReuseStrategy((request, response, context) -> false)
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableAuthCaching()
                .disableConnectionState()
                .disableContentCompression()
                .build();
    }

    private static Instant parseRetryAfter(org.apache.hc.core5.http.Header header, Instant now) {
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

    private static String topic(java.util.UUID eventId) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(eventId.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 32);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static WebPushResult failure(String outcome, String diagnostic) {
        return new WebPushResult(null, null, outcome, diagnostic);
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "Web Push submission failed";
        }
        var message = throwable.getMessage();
        return message.length() <= 1024 ? message : message.substring(0, 1024);
    }

    private static boolean isTimeout(Throwable throwable) {
        var current = throwable;
        while (current != null) {
            if (current instanceof InterruptedIOException
                    || current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void closeImmediately(CloseableHttpClient client) {
        if (client != null) {
            client.close(CloseMode.IMMEDIATE);
        }
    }

    private record PinnedDnsResolver(
            @NonNull String host,
            @NonNull InetAddress[] addresses
    ) implements DnsResolver {

        private PinnedDnsResolver {
            addresses = addresses.clone();
        }

        @Override
        public InetAddress[] resolve(String requestedHost) throws UnknownHostException {
            requireExpectedHost(requestedHost);
            return addresses.clone();
        }

        @Override
        public String resolveCanonicalHostname(String requestedHost) throws UnknownHostException {
            requireExpectedHost(requestedHost);
            return host;
        }

        private void requireExpectedHost(String requestedHost) throws UnknownHostException {
            if (!host.equalsIgnoreCase(requestedHost)) {
                throw new UnknownHostException(
                        "Refusing to resolve an unvalidated Push endpoint host"
                );
            }
        }
    }
}
