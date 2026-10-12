package pl.karolbystrek.kairos.api.monitoring.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsExportConfigurationTests {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGH";

    @Test
    void optionalCredentialFailsClosedForMissingWeakAndMalformedFiles() {
        assertThat(MetricsExportConfiguration.readToken(new FileSystemResource("/missing/metrics-token"))).isNull();
        for (var invalid : new String[]{"", "weak", "a".repeat(129), TOKEN + "\nextra", TOKEN + " "}) {
            assertThat(MetricsExportConfiguration.readToken(resource(invalid))).isNull();
        }
        assertThat(MetricsExportConfiguration.readToken(resource(TOKEN + "\n")))
                .isEqualTo(TOKEN.getBytes(StandardCharsets.US_ASCII));
    }

    private static ByteArrayResource resource(String value) {
        return new ByteArrayResource(value.getBytes(StandardCharsets.US_ASCII));
    }

}
