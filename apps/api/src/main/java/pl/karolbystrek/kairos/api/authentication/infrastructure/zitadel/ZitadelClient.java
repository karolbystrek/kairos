package pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import pl.karolbystrek.kairos.api.authentication.application.exception.InvalidLoginException;
import pl.karolbystrek.kairos.api.authentication.application.exception.RegistrationValidationException;
import pl.karolbystrek.kairos.api.authentication.infrastructure.config.AuthenticationProperties;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.Serializable;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

@Component
@Slf4j
public class ZitadelClient {
    public record ProviderSession(String id, String userId) implements Serializable {
        @Override public String toString() { return "ProviderSession[redacted]"; }
    }
    private final RestClient client;
    private final Clock clock;

    @Autowired
    public ZitadelClient(AuthenticationProperties properties, Clock clock) throws IOException {
        this.clock = clock;
        var transport = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build());
        transport.setReadTimeout(Duration.ofSeconds(10));
        var token = properties.tokenLocation().getContentAsString(StandardCharsets.UTF_8).strip();
        if (token.isBlank()) throw new IllegalArgumentException("ZITADEL service token is empty");
        client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(transport)
            .defaultHeader("Authorization", "Bearer " + token).build();
    }
    // Package-private constructor keeps HTTP contract tests independent of credentials.
    ZitadelClient(RestClient client, Clock clock) { this.client = client; this.clock = clock; }

    public String createUser(String email, String password) {
        var org = call(() -> client.get().uri("/management/v1/orgs/me").retrieve().body(JsonNode.class));
        var organization = required(org, "org", "id");
        var result = call(() -> client.post().uri("/v2/users/new").contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("organizationId", organization, "username", email, "human", Map.of(
                "profile", Map.of("givenName", email.split("@")[0], "familyName", "Kairos"),
                "email", Map.of("email", email, "returnCode", Map.of()),
                "password", Map.of("password", password, "changeRequired", false))))
            .retrieve().body(JsonNode.class));
        return required(result, "id");
    }

    public ProviderSession signIn(String email, String password) {
        JsonNode result;
        try {
            result = call(() -> client.post().uri("/v2/sessions").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("checks", Map.of("user", Map.of("loginName", email),
                    "password", Map.of("password", password))))
                .retrieve().body(JsonNode.class));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is4xxClientError()) throw new InvalidLoginException();
            throw exception;
        }
        var id = required(result, "sessionId");
        var state = getSession(id);
        var user = required(state, "session", "factors", "user", "id");
        var session = new ProviderSession(id, user);
        if (!valid(state, session)) {
            terminate(session);
            throw new InvalidLoginException();
        }
        return session;
    }

    public boolean isValid(ProviderSession session) {
        try { return valid(getSession(session.id()), session); }
        catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 404) return false;
            throw exception;
        }
    }

    private JsonNode getSession(String id) {
        return call(() -> client.get().uri("/v2/sessions/{id}", id).retrieve().body(JsonNode.class));
    }

    private boolean valid(JsonNode response, ProviderSession expected) {
        if (response == null) throw unavailable();
        var session = response.path("session");
        var factors = session.path("factors");
        if (!expected.userId().equals(factors.path("user").path("id").asText())
            || factors.path("password").path("verifiedAt").asText().isBlank()) return false;
        try {
            Instant.parse(factors.path("password").path("verifiedAt").asText());
            var expiry = session.path("expirationDate").asText();
            return expiry.isBlank() || Instant.parse(expiry).isAfter(clock.instant());
        } catch (RuntimeException exception) { throw unavailable(); }
    }

    public void changePassword(String subject, String current, String replacement) {
        call(() -> client.patch().uri("/v2/users/{id}", subject).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("human", Map.of("password", Map.of("password", Map.of("password", replacement,
                "changeRequired", false), "currentPassword", current))))
            .retrieve().body(JsonNode.class));
    }

    public void deleteUser(String subject) {
        call(() -> client.delete().uri("/v2/users/{id}", subject).retrieve().toBodilessEntity());
    }

    public void terminate(ProviderSession session) {
        try {
            call(() -> client.method(HttpMethod.DELETE)
                .uri("/v2/sessions/{id}", session.id()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of()).retrieve().toBodilessEntity());
        } catch (ResponseStatusException exception) {
            // Local revocation is authoritative for Kairos. Provider cleanup must not restore access.
            log.warn("Could not terminate ZITADEL session (status {})", exception.getStatusCode().value());
        }
    }

    private <T> T call(Supplier<T> operation) {
        try { return operation.get(); }
        catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity not found");
            if (exception.getStatusCode().value() == 409)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists");
            if (exception.getStatusCode().value() == 400) {
                // Translate only known ZITADEL v4 policy codes; never expose its response body.
                var messages = Map.of(
                    "DOMAIN-HuJf6", "Password is too short.",
                    "DOMAIN-co3Xw", "Include a lowercase letter.",
                    "DOMAIN-VoaRj", "Include an uppercase letter.",
                    "DOMAIN-ZBv4H", "Include a number.",
                    "DOMAIN-ZDLwA", "Include a symbol."
                );
                for (var entry : messages.entrySet()) {
                    if (exception.getResponseBodyAsString().contains("(" + entry.getKey() + ")")) {
                        throw new RegistrationValidationException(HttpStatus.BAD_REQUEST, "password", entry.getValue());
                    }
                }
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Check the current password and password requirements");
            }
            throw unavailable();
        } catch (RestClientException exception) { throw unavailable(); }
    }
    private static String required(JsonNode result, String... path) {
        if (result == null) throw unavailable();
        for (var key : path) result = result.path(key);
        var value = result.asText();
        if (value.isBlank()) throw unavailable();
        return value;
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Authentication provider is unavailable");
    }
}
