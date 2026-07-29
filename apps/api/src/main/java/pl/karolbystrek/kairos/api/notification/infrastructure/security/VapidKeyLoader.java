package pl.karolbystrek.kairos.api.notification.infrastructure.security;

import lombok.NonNull;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public final class VapidKeyLoader {

    private static final int P256_COORDINATE_BYTES = 32;

    private VapidKeyLoader() {
    }

    public static VapidKeyMaterial load(
            @NonNull Resource publicKeyResource,
            @NonNull Resource privateKeyResource
    ) {
        try {
            var keyFactory = KeyFactory.getInstance("EC");
            var publicKey = (ECPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(
                    readPem(publicKeyResource, "PUBLIC KEY")
            ));
            var privateKey = (ECPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(
                    readPem(privateKeyResource, "PRIVATE KEY")
            ));
            validatePair(publicKey, privateKey);
            var applicationServerKey = encodeUncompressed(publicKey);
            var base64 = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(applicationServerKey);
            var fingerprint = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(applicationServerKey));
            return new VapidKeyMaterial(
                    publicKey,
                    privateKey,
                    applicationServerKey,
                    base64,
                    fingerprint
            );
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("Could not load the configured VAPID P-256 key pair", exception);
        }
    }

    private static byte[] readPem(Resource resource, String label) throws IOException {
        try (var input = resource.getInputStream()) {
            var pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
            var encoded = pem
                    .replace("-----BEGIN " + label + "-----", "")
                    .replace("-----END " + label + "-----", "")
                    .replaceAll("\\s", "");
            if (encoded.isEmpty()) {
                throw new IllegalStateException("Configured VAPID resource is not a " + label);
            }
            return Base64.getDecoder().decode(encoded);
        }
    }

    private static void validatePair(ECPublicKey publicKey, ECPrivateKey privateKey)
            throws GeneralSecurityException {
        if (publicKey.getParams().getCurve().getField().getFieldSize() != 256
                || privateKey.getParams().getCurve().getField().getFieldSize() != 256) {
            throw new IllegalStateException("VAPID keys must use the P-256 curve");
        }
        var challenge = "kairos-vapid-key-pair".getBytes(StandardCharsets.US_ASCII);
        var signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey);
        signer.update(challenge);
        var signature = signer.sign();
        var verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey);
        verifier.update(challenge);
        if (!verifier.verify(signature)) {
            throw new IllegalStateException("Configured VAPID public and private keys do not form a pair");
        }
    }

    public static byte[] encodeUncompressed(ECPublicKey publicKey) {
        var encoded = new byte[1 + 2 * P256_COORDINATE_BYTES];
        encoded[0] = 0x04;
        copyCoordinate(publicKey.getW().getAffineX(), encoded, 1);
        copyCoordinate(publicKey.getW().getAffineY(), encoded, 1 + P256_COORDINATE_BYTES);
        return encoded;
    }

    private static void copyCoordinate(BigInteger coordinate, byte[] destination, int offset) {
        var source = coordinate.toByteArray();
        var sourceOffset = Math.max(0, source.length - P256_COORDINATE_BYTES);
        var length = Math.min(source.length, P256_COORDINATE_BYTES);
        Arrays.fill(destination, offset, offset + P256_COORDINATE_BYTES, (byte) 0);
        System.arraycopy(
                source,
                sourceOffset,
                destination,
                offset + P256_COORDINATE_BYTES - length,
                length
        );
    }
}
