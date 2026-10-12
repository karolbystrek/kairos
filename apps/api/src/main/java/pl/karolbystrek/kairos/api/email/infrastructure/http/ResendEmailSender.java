package pl.karolbystrek.kairos.api.email.infrastructure.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pl.karolbystrek.kairos.api.email.infrastructure.config.EmailProperties;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
public class ResendEmailSender {
    public enum Result { SENT, RETRY, FAILED }
    private final RestClient client;
    private final boolean logOnly;

    @Autowired
    public ResendEmailSender(EmailProperties properties) {
        var transport = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        transport.setReadTimeout(Duration.ofSeconds(10));
        client = RestClient.builder().baseUrl("https://api.resend.com").requestFactory(transport)
            .defaultHeader("Authorization", "Bearer " + properties.apiKey()).build();
        logOnly = properties.mode() == EmailProperties.Mode.LOG;
    }

    ResendEmailSender(RestClient client) { this.client = client; logOnly = false; }

    public Result send(UUID id, byte[] payload) {
        if (logOnly) {
            log.info("Email delivery {} accepted by local logging adapter; content and recipient redacted", id);
            return Result.SENT;
        }
        try {
            client.post().uri("/emails").header("Idempotency-Key", "kairos-email-" + id)
                .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().toBodilessEntity();
            return Result.SENT;
        } catch (RestClientResponseException exception) {
            var status = exception.getStatusCode().value();
            return status == 408 || status == 429 || status == 409 || status >= 500 ? Result.RETRY : Result.FAILED;
        } catch (RestClientException exception) {
            return Result.RETRY;
        }
    }
}
