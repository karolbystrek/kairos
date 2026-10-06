package pl.karolbystrek.kairos.api.authentication.testsupport;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

public class TestNotificationKeyLauncherSessionListener implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        try {
            var classpathRoot = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            var keyDirectory = Path.of(classpathRoot).resolve("keys");
            Files.createDirectories(keyDirectory);
            var vapidGenerator = KeyPairGenerator.getInstance("EC");
            vapidGenerator.initialize(new ECGenParameterSpec("secp256r1"));
            var vapidKeyPair = vapidGenerator.generateKeyPair();
            writePem(
                    keyDirectory.resolve("test-vapid-public.pem"),
                    "PUBLIC KEY",
                    vapidKeyPair.getPublic().getEncoded()
            );
            writePem(
                    keyDirectory.resolve("test-vapid-private.pem"),
                    "PRIVATE KEY",
                    vapidKeyPair.getPrivate().getEncoded()
            );
        } catch (GeneralSecurityException | IOException | URISyntaxException exception) {
            throw new IllegalStateException("Could not generate the notification key pair for backend tests", exception);
        }
    }

    private static void writePem(Path path, String type, byte[] encoded) throws IOException {
        var body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
            .encodeToString(encoded);
        var pem = """
            -----BEGIN %s-----
            %s
            -----END %s-----
            """.formatted(type, body, type);
        Files.writeString(path, pem, StandardCharsets.US_ASCII);
    }
}
