package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http;

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
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config.WebhookProperties;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class WebhookHttpClient {

    private static final String CONTENT_TYPE = "application/cloudevents+json";
    private static final int MAXIMUM_RESPONSE_LINE_LENGTH = 8_192;
    private static final int MAXIMUM_RESPONSE_HEADER_COUNT = 100;
    private static final int MAXIMUM_LEADING_EMPTY_LINES = 10;

    private final WebhookDestinationPolicy destinationPolicy;
    private final WebhookProperties properties;

    public WebhookHttpResult post(String destination, String body, String signatureHeader) {
        var timeout = properties.delivery().totalTimeout();
        var deadlineExceeded = new AtomicBoolean();
        var clientReference = new AtomicReference<CloseableHttpClient>();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var future = executor.submit(() -> execute(
                    destination,
                    body,
                    signatureHeader,
                    deadlineExceeded,
                    clientReference
            ));
            try {
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                deadlineExceeded.set(true);
                future.cancel(true);
                closeImmediately(clientReference.get());
                return failure("TIMEOUT", "Webhook delivery exceeded the total timeout");
            } catch (ExecutionException exception) {
                return failure("NETWORK_ERROR", safeMessage(exception.getCause()));
            } catch (InterruptedException exception) {
                deadlineExceeded.set(true);
                future.cancel(true);
                closeImmediately(clientReference.get());
                Thread.currentThread().interrupt();
                return failure("INTERRUPTED", "Webhook delivery was interrupted");
            }
        } finally {
            executor.shutdownNow();
            closeImmediately(clientReference.get());
        }
    }

    private WebhookHttpResult execute(
            String destination,
            String body,
            String signatureHeader,
            AtomicBoolean deadlineExceeded,
            AtomicReference<CloseableHttpClient> clientReference
    ) throws IOException {
        WebhookDestinationPolicy.AllowedDestination allowedDestination;
        try {
            allowedDestination = destinationPolicy.requireAllowedAndResolve(destination);
        } catch (InvalidWebhookDestinationException exception) {
            return failure("DESTINATION_BLOCKED", safeMessage(exception));
        }
        if (deadlineExceeded.get() || Thread.currentThread().isInterrupted()) {
            return failure("TIMEOUT", "Webhook delivery exceeded the total timeout");
        }

        var client = buildClient(allowedDestination);
        clientReference.set(client);
        if (deadlineExceeded.get()) {
            closeImmediately(client);
            clientReference.compareAndSet(client, null);
            return failure("TIMEOUT", "Webhook delivery exceeded the total timeout");
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
            clientReference.compareAndSet(client, null);
        }
    }

    private CloseableHttpClient buildClient(
            WebhookDestinationPolicy.AllowedDestination allowedDestination
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
                        allowedDestination.uri().getHost(),
                        allowedDestination.addresses()
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

    private static void closeImmediately(CloseableHttpClient client) {
        if (client != null) {
            client.close(CloseMode.IMMEDIATE);
        }
    }

    private static WebhookHttpResult failure(String errorType, String detail) {
        return new WebhookHttpResult(null, null, false, errorType, detail);
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "Webhook delivery failed";
        }
        var message = throwable.getMessage();
        return message.length() <= 1024 ? message : message.substring(0, 1024);
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
                        "Refusing to resolve an unvalidated webhook destination host"
                );
            }
        }
    }
}
