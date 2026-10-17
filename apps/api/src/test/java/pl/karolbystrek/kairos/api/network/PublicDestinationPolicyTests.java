package pl.karolbystrek.kairos.api.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicDestinationPolicyTests {

    private final PublicDestinationPolicy production = new PublicDestinationPolicy(false, "Destination");

    @ParameterizedTest
    @ValueSource(strings = {
            "http://8.8.8.8/events", "https://127.0.0.1/events", "https://169.254.169.254/latest",
            "https://[::1]/events", "https://[::ffff:169.254.169.254]/latest",
            "https://10.0.0.1/", "https://172.16.0.1/", "https://192.168.1.1/", "https://100.64.0.1/",
            "https://0.0.0.0/", "https://[fc00::1]/", "https://user@8.8.8.8/", "https://8.8.8.8/#f",
            "https://8.8.8.8:0/", "https://8.8.8.8:70000/", "file:///etc/passwd", "ftp://8.8.8.8/", "  "
    })
    void productionRejectsInsecurePrivateSpecialOrMalformedDestinations(String destination) {
        assertThatThrownBy(() -> production.requireAllowed(destination))
                .isInstanceOf(InvalidDestinationException.class);
    }

    @Test
    void productionAllowsPublicHttpsAndReturnsDefensiveDnsSnapshots() {
        var allowed = production.requireAllowedAndResolve("https://8.8.8.8/events");
        var firstSnapshot = allowed.addresses();
        firstSnapshot[0] = null;

        assertThat(allowed.uri().toASCIIString()).isEqualTo("https://8.8.8.8/events");
        assertThat(allowed.addresses()).doesNotContainNull();
    }

    @Test
    void localDevelopmentExplicitlyAllowsHttpAndPrivateAddresses() {
        var policy = new PublicDestinationPolicy(true, "Destination");

        assertThat(policy.requireAllowed("http://127.0.0.1:9080/events").toString())
                .isEqualTo("http://127.0.0.1:9080/events");
    }

    @Test
    void preservesEncodedPathAndQueryDelimiters() {
        var destination = "https://8.8.8.8/hooks/a%2Fb?token=x%26role%3Dadmin%25";
        assertThat(production.requireAllowed(destination).toString()).isEqualTo(destination);
    }
}
