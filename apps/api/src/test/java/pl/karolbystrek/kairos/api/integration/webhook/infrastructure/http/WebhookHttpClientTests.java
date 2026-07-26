package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config.WebhookProperties;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookHttpClientTests {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void doesNotFollowRedirectsAndBoundsResponseBodiesAndHeaders() throws Exception {
        var redirectedRequests = new AtomicInteger();
        server = startServer();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/redirected");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirected", exchange -> {
            redirectedRequests.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/large", exchange -> respond(
                exchange,
                200,
                "x".repeat(2_048)
        ));
        server.createContext("/headers", exchange -> {
            for (var index = 0; index < 101; index++) {
                exchange.getResponseHeaders().add("X-Bounded-" + index, "value");
            }
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        var client = localClient(Duration.ofSeconds(2), 1_024);
        var redirect = client.post(url("/redirect"), "{}", "t=1,v1=signature");
        var large = client.post(url("/large"), "{}", "t=1,v1=signature");
        var excessiveHeaders = client.post(
                url("/headers"),
                "{}",
                "t=1,v1=signature"
        );

        assertThat(redirect.statusCode()).isEqualTo(302);
        assertThat(redirect.errorType()).isEqualTo("NON_2XX_RESPONSE");
        assertThat(redirectedRequests).hasValue(0);
        assertThat(large.isSuccessful()).isTrue();
        assertThat(large.responseTruncated()).isTrue();
        assertThat(large.responseBody().getBytes(StandardCharsets.UTF_8))
                .hasSize(1_024);
        assertThat(excessiveHeaders.errorType()).isEqualTo("NETWORK_ERROR");
    }

    @Test
    void enforcesOneTotalTimeoutAcrossTheDelivery() throws Exception {
        server = startServer();
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(500);
                respond(exchange, 204, "");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });

        var client = localClient(Duration.ofMillis(50), 1_024);
        var result = client.post(url("/slow"), "{}", "t=1,v1=signature");

        assertThat(result.errorType()).isEqualTo("TIMEOUT");
        assertThat(result.statusCode()).isNull();
    }

    @Test
    void revalidatesEveryDeliveryAndPinsTheValidatedAddresses() throws Exception {
        server = startServer();
        server.createContext("/pinned", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        var destination = "http://unresolvable.invalid:"
                + server.getAddress().getPort()
                + "/pinned";
        var destinationPolicy = mock(WebhookDestinationPolicy.class);
        when(destinationPolicy.requireAllowedAndResolve(destination))
                .thenReturn(new WebhookDestinationPolicy.AllowedDestination(
                        URI.create(destination),
                        new InetAddress[]{InetAddress.getLoopbackAddress()}
                ));
        var client = new WebhookHttpClient(
                destinationPolicy,
                properties(Duration.ofSeconds(2), 1_024)
        );

        assertThat(client.post(destination, "{}", "t=1,v1=signature").statusCode())
                .isEqualTo(204);
        assertThat(client.post(destination, "{}", "t=2,v1=signature").statusCode())
                .isEqualTo(204);
        verify(destinationPolicy, times(2)).requireAllowedAndResolve(destination);
    }

    private WebhookHttpClient localClient(Duration timeout, int maximumBodyBytes) {
        return new WebhookHttpClient(
                new WebhookDestinationPolicy(properties(timeout, maximumBodyBytes)),
                properties(timeout, maximumBodyBytes)
        );
    }

    private static WebhookProperties properties(
            Duration timeout,
            int maximumBodyBytes
    ) {
        return new WebhookProperties(
                new WebhookProperties.Signing("unused", Duration.ofHours(24)),
                new WebhookProperties.Delivery(timeout, maximumBodyBytes),
                new WebhookProperties.Worker(10, timeout.plusSeconds(1)),
                WebhookProperties.DestinationPolicy.LOCAL_DEVELOPMENT
        );
    }

    private HttpServer startServer() throws IOException {
        var created = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                0
        );
        created.start();
        return created;
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }
}
