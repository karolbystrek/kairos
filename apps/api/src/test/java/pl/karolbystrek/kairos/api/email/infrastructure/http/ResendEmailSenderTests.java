package pl.karolbystrek.kairos.api.email.infrastructure.http;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.karolbystrek.kairos.api.email.infrastructure.config.EmailConfiguration;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendEmailSenderTests {
    @Test
    void retriesPreserveTheSameProviderIdempotencyKeyAndContent() {
        var builder = RestClient.builder().baseUrl("https://api.resend.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var id = UUID.randomUUID();
        var payload = "{\"from\":\"Kairos <no-reply@kairos.karolbystrek.pl>\",\"to\":\"owner@example.com\",\"subject\":\"Verify your email\",\"text\":\"Your code is 123456\"}";
        server.expect(requestTo("https://api.resend.com/emails"))
            .andExpect(header("Idempotency-Key", "kairos-email-" + id)).andExpect(content().json(payload))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.resend.com/emails"))
            .andExpect(header("Idempotency-Key", "kairos-email-" + id)).andExpect(content().json(payload))
            .andRespond(withSuccess("{\"id\":\"sent\"}", MediaType.APPLICATION_JSON));
        var sender = new ResendEmailSender(builder.build());
        assertThat(sender.send(id, payload.getBytes(StandardCharsets.UTF_8))).isEqualTo(ResendEmailSender.Result.RETRY);
        assertThat(sender.send(id, payload.getBytes(StandardCharsets.UTF_8))).isEqualTo(ResendEmailSender.Result.SENT);
        server.verify();
    }

    @Test
    void permanentProviderRejectionDoesNotRetry() {
        var builder = RestClient.builder().baseUrl("https://api.resend.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.resend.com/emails")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT));
        assertThat(new ResendEmailSender(builder.build()).send(UUID.randomUUID(), new byte[0]))
            .isEqualTo(ResendEmailSender.Result.FAILED);
        server.verify();
    }

    @Test
    void productionRequiresProviderCredentialsAndCannotEnableLoggingMode() {
        new ApplicationContextRunner().withUserConfiguration(EmailConfiguration.class)
            .run(context -> assertThat(context).hasFailed());
        new ApplicationContextRunner().withUserConfiguration(EmailConfiguration.class)
            .withPropertyValues("kairos.email.mode=LOG")
            .run(context -> assertThat(context).hasFailed());
    }
}
