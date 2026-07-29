package pl.karolbystrek.kairos.api.notification.infrastructure.security;

import org.junit.jupiter.api.Test;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class WebPushPayloadEncryptorTests {

    private static final Base64.Decoder BASE64_URL = Base64.getUrlDecoder();

    @Test
    void encryptsThePublishedRfc8291Example() throws Exception {
        var encryptor = new WebPushPayloadEncryptor(properties());
        var parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        var ec = parameters.getParameterSpec(java.security.spec.ECParameterSpec.class);
        var senderPrivateKey = KeyFactory.getInstance("EC").generatePrivate(
                new ECPrivateKeySpec(unsignedInteger(
                        "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"
                ), ec)
        );
        var encrypted = encryptor.encryptWithParameters(
                "When I grow up, I want to be a watermelon"
                        .getBytes(StandardCharsets.US_ASCII),
                decode(
                        "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcx"
                                + "aOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"
                ),
                decode("BTBZMqHH6r4Tts7J_aSIgg"),
                senderPrivateKey,
                decode(
                        "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIg"
                                + "Dll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"
                ),
                decode("DGv6ra1nlYgDCS1FRnbzlw")
        );

        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted))
                .isEqualTo(
                        "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml"
                                + "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT"
                                + "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN"
                );
    }

    private static BigInteger unsignedInteger(String value) {
        return new BigInteger(1, decode(value));
    }

    private static byte[] decode(String value) {
        return BASE64_URL.decode(value);
    }

    private static CustomerNotificationProperties properties() {
        return new CustomerNotificationProperties(
                new CustomerNotificationProperties.Vapid("public", "private", "mailto:test"),
                new CustomerNotificationProperties.Subscription(
                        "encryption",
                        10,
                        Duration.ofDays(30)
                ),
                new CustomerNotificationProperties.Delivery(
                        Duration.ofMinutes(10),
                        8,
                        Duration.ofSeconds(5),
                        Duration.ofMinutes(2),
                        Duration.ofSeconds(10),
                        Duration.ofDays(7),
                        Duration.ofDays(30),
                        3_072
                ),
                new CustomerNotificationProperties.Worker(100, Duration.ofSeconds(30)),
                CustomerNotificationProperties.DestinationPolicy.PUBLIC_HTTPS
        );
    }
}
