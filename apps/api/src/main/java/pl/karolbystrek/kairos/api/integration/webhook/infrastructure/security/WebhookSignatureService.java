package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.security;

import lombok.NonNull;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Component
public class WebhookSignatureService {

    public String createHeader(
            @NonNull Instant deliveryTimestamp,
            @NonNull String exactBody,
            @NonNull List<byte[]> signingSecrets
    ) {
        if (signingSecrets.isEmpty()) {
            throw new IllegalArgumentException("At least one webhook signing secret is required");
        }

        var timestamp = Long.toString(deliveryTimestamp.getEpochSecond());
        var stringToSign = (timestamp + "." + exactBody).getBytes(StandardCharsets.UTF_8);
        var header = new StringBuilder("t=").append(timestamp);
        for (var signingSecret : signingSecrets) {
            header.append(",v1=").append(sign(signingSecret, stringToSign));
        }
        return header.toString();
    }

    private static String sign(byte[] signingSecret, byte[] stringToSign) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(stringToSign));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not sign a webhook delivery", exception);
        }
    }
}
