package pl.karolbystrek.kairos.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiContextPathIntegrationTests {

    @LocalServerPort
    private int port;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Test
    void appliesTheConfiguredContextPathToControllersAndActuator() throws Exception {
        assertThat(contextPath).isEqualTo("/api");
        assertThat(statusCode("/api/auth/csrf")).isEqualTo(200);
        assertThat(statusCode("/auth/csrf")).isEqualTo(404);
        assertThat(statusCode("/api/actuator/health")).isEqualTo(200);
        assertThat(statusCode("/actuator/health")).isEqualTo(404);
    }

    private int statusCode(String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }
}
