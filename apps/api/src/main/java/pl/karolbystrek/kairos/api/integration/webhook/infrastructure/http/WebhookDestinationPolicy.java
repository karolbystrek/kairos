package pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config.WebhookProperties;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class WebhookDestinationPolicy {

    private final WebhookProperties properties;

    public URI requireAllowed(String candidate) {
        return requireAllowedAndResolve(candidate).uri();
    }

    public AllowedDestination requireAllowedAndResolve(String candidate) {
        var uri = parse(candidate);
        var addresses = requireAllowed(uri);
        return new AllowedDestination(uri, addresses);
    }

    private InetAddress[] requireAllowed(URI uri) {
        var scheme = uri.getScheme();
        if (scheme == null) {
            throw new InvalidWebhookDestinationException("Webhook destination requires a URL scheme");
        }
        var normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        var localDevelopment =
                properties.destinationPolicy() == WebhookProperties.DestinationPolicy.LOCAL_DEVELOPMENT;
        if (localDevelopment) {
            if (!normalizedScheme.equals("https") && !normalizedScheme.equals("http")) {
                throw new InvalidWebhookDestinationException(
                        "Local webhook destinations must use HTTP or HTTPS"
                );
            }
        } else if (!normalizedScheme.equals("https")) {
            throw new InvalidWebhookDestinationException(
                    "Production webhook destinations must use HTTPS"
            );
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new InvalidWebhookDestinationException(
                    "Webhook destinations cannot contain user information or fragments"
            );
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidWebhookDestinationException("Webhook destination requires a host");
        }
        if (uri.getPort() == 0 || uri.getPort() < -1 || uri.getPort() > 65535) {
            throw new InvalidWebhookDestinationException("Webhook destination port is invalid");
        }

        var addresses = resolve(uri.getHost());
        if (!localDevelopment) {
            for (var address : addresses) {
                if (!isPublic(address)) {
                    throw new InvalidWebhookDestinationException(
                            "Webhook destination must resolve only to public addresses"
                    );
                }
            }
        }
        return addresses;
    }

    private static URI parse(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new InvalidWebhookDestinationException("Webhook destination is required");
        }
        try {
            var parsed = new URI(candidate.strip());
            if (parsed.getHost() == null) {
                return parsed;
            }
            var asciiHost = parsed.getHost().contains(":")
                    ? parsed.getHost()
                    : IDN.toASCII(parsed.getHost(), IDN.USE_STD3_ASCII_RULES);
            return new URI(
                    parsed.getScheme(),
                    parsed.getUserInfo(),
                    asciiHost,
                    parsed.getPort(),
                    parsed.getPath(),
                    parsed.getQuery(),
                    parsed.getFragment()
            );
        } catch (IllegalArgumentException | URISyntaxException exception) {
            throw new InvalidWebhookDestinationException("Webhook destination is not a valid URL", exception);
        }
    }

    private static InetAddress[] resolve(String host) {
        try {
            var addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw new InvalidWebhookDestinationException(
                        "Webhook destination host did not resolve"
                );
            }
            return addresses;
        } catch (UnknownHostException exception) {
            throw new InvalidWebhookDestinationException(
                    "Webhook destination host could not be resolved",
                    exception
            );
        }
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address ipv4) {
            return isPublicIpv4(ipv4.getAddress());
        }
        if (address instanceof Inet6Address ipv6) {
            return isPublicIpv6(ipv6.getAddress());
        }
        return false;
    }

    private static boolean isPublicIpv4(byte[] address) {
        var first = Byte.toUnsignedInt(address[0]);
        var second = Byte.toUnsignedInt(address[1]);
        var third = Byte.toUnsignedInt(address[2]);

        return first != 0
                && first != 10
                && first != 127
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 169 && second == 254)
                && !(first == 172 && second >= 16 && second <= 31)
                && !(first == 192 && second == 0 && third == 0)
                && !(first == 192 && second == 0 && third == 2)
                && !(first == 192 && second == 88 && third == 99)
                && !(first == 192 && second == 168)
                && !(first == 198 && (second == 18 || second == 19))
                && !(first == 198 && second == 51 && third == 100)
                && !(first == 203 && second == 0 && third == 113)
                && first < 224;
    }

    private static boolean isPublicIpv6(byte[] address) {
        if (isIpv4Mapped(address)) {
            var ipv4 = new byte[]{address[12], address[13], address[14], address[15]};
            return isPublicIpv4(ipv4);
        }
        var first = Byte.toUnsignedInt(address[0]);
        var second = Byte.toUnsignedInt(address[1]);
        var third = Byte.toUnsignedInt(address[2]);
        var fourth = Byte.toUnsignedInt(address[3]);
        if ((first & 0xe0) != 0x20) {
            return false;
        }
        // Reject IANA special-purpose or reserved ranges inside 2000::/3 that
        // are not ordinary globally reachable unicast destinations.
        if (first == 0x20 && second == 0x01
                && ((third & 0xfe) == 0 || (third == 0x0d && fourth == 0xb8))) {
            return false;
        }
        if (first == 0x20 && second == 0x02) {
            return false;
        }
        if (first == 0x3f) {
            return false;
        }
        return true;
    }

    private static boolean isIpv4Mapped(byte[] address) {
        for (var index = 0; index < 10; index++) {
            if (address[index] != 0) {
                return false;
            }
        }
        return address[10] == (byte) 0xff && address[11] == (byte) 0xff;
    }

    public record AllowedDestination(
            @NonNull URI uri,
            @NonNull InetAddress[] addresses
    ) {

        public AllowedDestination {
            addresses = addresses.clone();
        }

        @Override
        public InetAddress[] addresses() {
            return addresses.clone();
        }
    }
}
