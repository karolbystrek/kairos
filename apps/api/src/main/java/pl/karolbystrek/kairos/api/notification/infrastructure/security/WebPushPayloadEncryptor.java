package pl.karolbystrek.kairos.api.notification.infrastructure.security;

import lombok.NonNull;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.exception.InvalidCustomerPushSubscriptionException;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;

@Component
public class WebPushPayloadEncryptor {

    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int CONTENT_ENCRYPTION_KEY_BYTES = 16;
    private static final int HKDF_KEY_BYTES = 32;
    private static final int RECORD_SIZE = 4096;
    private static final int AUTHENTICATION_TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final int maximumPayloadBytes;

    public WebPushPayloadEncryptor(CustomerNotificationProperties properties) {
        maximumPayloadBytes = properties.delivery().maximumPayloadBytes();
    }

    public byte[] encrypt(
            byte @NonNull [] plaintext,
            byte @NonNull [] userAgentPublicKey,
            byte @NonNull [] authSecret
    ) {
        if (plaintext.length > maximumPayloadBytes) {
            throw new IllegalArgumentException("Customer Push payload exceeds its configured limit");
        }
        try {
            var keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"), secureRandom);
            var serverKeyPair = keyPairGenerator.generateKeyPair();
            var serverPublicKey = VapidKeyLoader.encodeUncompressed(
                    (ECPublicKey) serverKeyPair.getPublic()
            );
            var salt = new byte[SALT_BYTES];
            secureRandom.nextBytes(salt);
            return encryptWithParameters(
                    plaintext,
                    userAgentPublicKey,
                    authSecret,
                    serverKeyPair.getPrivate(),
                    serverPublicKey,
                    salt
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt a Web Push payload", exception);
        }
    }

    byte[] encryptWithParameters(
            byte[] plaintext,
            byte[] userAgentPublicKey,
            byte[] authSecret,
            PrivateKey serverPrivateKey,
            byte[] serverPublicKey,
            byte[] salt
    ) throws GeneralSecurityException {
        if (salt.length != SALT_BYTES
                || serverPublicKey.length != 65
                || serverPublicKey[0] != 0x04) {
            throw new IllegalArgumentException("Web Push encryption parameters are invalid");
        }
        var userPublicKey = decodePublicKey(userAgentPublicKey);
        var agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(serverPrivateKey);
        agreement.doPhase(userPublicKey, true);
        var sharedSecret = agreement.generateSecret();
        var authPrk = hmac(authSecret, sharedSecret);
        var keyInfo = concatenate(
                "WebPush: info\0".getBytes(StandardCharsets.US_ASCII),
                userAgentPublicKey,
                serverPublicKey
        );
        var inputKeyMaterial = hkdfExpand(authPrk, keyInfo, HKDF_KEY_BYTES);
        var prk = hmac(salt, inputKeyMaterial);
        var contentEncryptionKey = hkdfExpand(
                prk,
                "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII),
                CONTENT_ENCRYPTION_KEY_BYTES
        );
        var nonce = hkdfExpand(
                prk,
                "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII),
                NONCE_BYTES
        );
        var record = java.util.Arrays.copyOf(plaintext, plaintext.length + 1);
        record[record.length - 1] = 0x02;
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(contentEncryptionKey, "AES"),
                new GCMParameterSpec(AUTHENTICATION_TAG_BITS, nonce)
        );
        var ciphertext = cipher.doFinal(record);
        var output = new ByteArrayOutputStream();
        output.writeBytes(salt);
        output.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(RECORD_SIZE).array());
        output.write(serverPublicKey.length);
        output.writeBytes(serverPublicKey);
        output.writeBytes(ciphertext);
        return output.toByteArray();
    }

    private static ECPublicKey decodePublicKey(byte[] encoded) throws GeneralSecurityException {
        if (encoded.length != 65 || encoded[0] != 0x04) {
            throw new InvalidCustomerPushSubscriptionException(
                    "Push subscription p256dh must be an uncompressed P-256 public key"
            );
        }
        var parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        var specification = parameters.getParameterSpec(java.security.spec.ECParameterSpec.class);
        var point = new ECPoint(
                new java.math.BigInteger(1, java.util.Arrays.copyOfRange(encoded, 1, 33)),
                new java.math.BigInteger(1, java.util.Arrays.copyOfRange(encoded, 33, 65))
        );
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(
                new ECPublicKeySpec(point, specification)
        );
    }

    private static byte[] hmac(byte[] key, byte[] value) throws GeneralSecurityException {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value);
    }

    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length)
            throws GeneralSecurityException {
        var result = new byte[length];
        var previous = new byte[0];
        var offset = 0;
        var counter = 1;
        while (offset < length) {
            previous = hmac(prk, concatenate(previous, info, new byte[]{(byte) counter}));
            var copied = Math.min(previous.length, length - offset);
            System.arraycopy(previous, 0, result, offset, copied);
            offset += copied;
            counter++;
        }
        return result;
    }

    private static byte[] concatenate(byte[]... values) {
        var output = new ByteArrayOutputStream();
        for (var value : values) {
            output.writeBytes(value);
        }
        return output.toByteArray();
    }
}
