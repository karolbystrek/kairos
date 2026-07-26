package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureServiceTests {

    private final WebhookSignatureService signatureService = new WebhookSignatureService();

    @Test
    void signsTheTimestampAndExactUtf8BodyWithCurrentAndPreviousSecrets() {
        var header = signatureService.createHeader(
                Instant.ofEpochSecond(1_720_000_000L),
                "{\"id\":\"1\"}",
                List.of(
                        "secret".getBytes(StandardCharsets.UTF_8),
                        "previous".getBytes(StandardCharsets.UTF_8)
                )
        );

        assertThat(header).isEqualTo(
                "t=1720000000,"
                        + "v1=b8a6a714904f85f015ced17aad514b594d0e65cd5712d12e521fc77f65354dd7,"
                        + "v1=b05c0349eb02e2cbbaef56a556ba1f76da90569f5013b6ce98ae8cb56ba664a0"
        );
    }
}
