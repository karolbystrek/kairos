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
import java.util.Base64;

public class TestJwtKeyLauncherSessionListener implements LauncherSessionListener {

    private static final int TEST_RSA_KEY_SIZE = 2048;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(TEST_RSA_KEY_SIZE);
            var keyPair = generator.generateKeyPair();
            var classpathRoot = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            var keyDirectory = Path.of(classpathRoot).resolve("keys");
            Files.createDirectories(keyDirectory);
            writePem(keyDirectory.resolve("test-jwt-public.pem"), "PUBLIC KEY", keyPair.getPublic().getEncoded());
            writePem(keyDirectory.resolve("test-jwt-private.pem"), "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        } catch (GeneralSecurityException | IOException | URISyntaxException exception) {
            throw new IllegalStateException("Could not generate the JWT key pair for backend tests", exception);
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
