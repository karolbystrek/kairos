package pl.karolbystrek.kairos.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.exception.InvalidCustomerPushSubscriptionException;
import pl.karolbystrek.kairos.api.notification.application.model.CustomerPushSubscriptionInput;
import pl.karolbystrek.kairos.api.notification.application.model.ValidatedPushSubscription;
import pl.karolbystrek.kairos.api.notification.infrastructure.http.PushDestinationPolicy;

import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Base64;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
class CustomerPushSubscriptionValidator {

    private static final int P256_KEY_BYTES = 65;
    private static final int AUTH_SECRET_BYTES = 16;

    private final PushDestinationPolicy destinationPolicy;

    ValidatedPushSubscription validate(CustomerPushSubscriptionInput input) {
        var destination = destinationPolicy.requireAllowedAndResolve(input.endpoint());
        var p256dh = decode(input.p256dh(), "p256dh");
        var auth = decode(input.auth(), "auth");
        requireValidP256Key(p256dh);
        if (auth.length != AUTH_SECRET_BYTES) {
            throw invalid("Push subscription auth secret must contain exactly 16 bytes");
        }
        var endpoint = destination.uri().toString();
        return new ValidatedPushSubscription(
                endpoint,
                sha256(endpoint),
                destination.origin(),
                p256dh,
                auth,
                input.expirationTime()
        );
    }

    private static byte[] decode(String value, String name) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidCustomerPushSubscriptionException(
                    "Push subscription " + name + " is not valid base64url",
                    exception
            );
        }
    }

    private static void requireValidP256Key(byte[] encoded) {
        if (encoded.length != P256_KEY_BYTES || encoded[0] != 0x04) {
            throw invalid("Push subscription p256dh must be an uncompressed P-256 public key");
        }
        try {
            var parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            var specification = parameters.getParameterSpec(java.security.spec.ECParameterSpec.class);
            var coordinateBytes = (P256_KEY_BYTES - 1) / 2;
            var x = new java.math.BigInteger(1, java.util.Arrays.copyOfRange(
                    encoded,
                    1,
                    1 + coordinateBytes
            ));
            var y = new java.math.BigInteger(1, java.util.Arrays.copyOfRange(
                    encoded,
                    1 + coordinateBytes,
                    encoded.length
            ));
            KeyFactory.getInstance("EC").generatePublic(
                    new ECPublicKeySpec(new ECPoint(x, y), specification)
            );
        } catch (GeneralSecurityException exception) {
            throw new InvalidCustomerPushSubscriptionException(
                    "Push subscription p256dh is not a valid P-256 public key",
                    exception
            );
        }
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static InvalidCustomerPushSubscriptionException invalid(String message) {
        return new InvalidCustomerPushSubscriptionException(message);
    }
}
