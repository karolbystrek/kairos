package pl.karolbystrek.kairos.api.network;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicDestinationPolicyTests {

    private final PublicDestinationPolicy production = new PublicDestinationPolicy(false, "Destination");

    @Test
    void productionRequiresHttpsAndRejectsPrivateOrSpecialAddresses() {
        assertThatThrownBy(() -> production.requireAllowed("http://8.8.8.8/events"))
                .isInstanceOf(InvalidDestinationException.class);
        assertThatThrownBy(() -> production.requireAllowed("https://127.0.0.1/events"))
                .isInstanceOf(InvalidDestinationException.class);
        assertThatThrownBy(() -> production.requireAllowed("https://169.254.169.254/latest"))
                .isInstanceOf(InvalidDestinationException.class);
        assertThatThrownBy(() -> production.requireAllowed("https://[::1]/events"))
                .isInstanceOf(InvalidDestinationException.class);
        assertThatThrownBy(() -> production.requireAllowed("https://[::ffff:169.254.169.254]/latest"))
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
