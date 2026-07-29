package pl.karolbystrek.kairos.api.notification.infrastructure.http;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.notification.application.exception.InvalidCustomerPushSubscriptionException;
import pl.karolbystrek.kairos.api.notification.infrastructure.config.CustomerNotificationProperties;

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
public class PushDestinationPolicy {

    private final CustomerNotificationProperties properties;

    public AllowedPushDestination requireAllowedAndResolve(String candidate) {
        var uri = parse(candidate);
        var scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        var localDevelopment =
                properties.destinationPolicy()
                        == CustomerNotificationProperties.DestinationPolicy.LOCAL_DEVELOPMENT;
        if (localDevelopment) {
            if (!scheme.equals("https") && !scheme.equals("http")) {
                throw invalid("Local Push endpoints must use HTTP or HTTPS");
            }
        } else if (!scheme.equals("https")) {
            throw invalid("Push endpoints must use HTTPS");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw invalid("Push endpoints cannot contain user information or fragments");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw invalid("Push endpoint requires a host");
        }
        if (uri.getPort() == 0 || uri.getPort() < -1 || uri.getPort() > 65535) {
            throw invalid("Push endpoint port is invalid");
        }
        var addresses = resolve(uri.getHost());
        if (!localDevelopment) {
            for (var address : addresses) {
                if (!isPublic(address)) {
                    throw invalid("Push endpoint must resolve only to public addresses");
                }
            }
        }
        return new AllowedPushDestination(uri, addresses, origin(uri));
    }

    private static URI parse(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw invalid("Push endpoint is required");
        }
        try {
            var parsed = new URI(candidate.strip());
            if (parsed.getScheme() == null || parsed.getHost() == null) {
                throw invalid("Push endpoint must be an absolute URL");
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
            throw new InvalidCustomerPushSubscriptionException(
                    "Push endpoint is not a valid URL",
                    exception
            );
        }
    }

    private static InetAddress[] resolve(String host) {
        try {
            var addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw invalid("Push endpoint host did not resolve");
            }
            return addresses;
        } catch (UnknownHostException exception) {
            throw new InvalidCustomerPushSubscriptionException(
                    "Push endpoint host could not be resolved",
                    exception
            );
        }
    }

    private static String origin(URI uri) {
        try {
            return new URI(
                    uri.getScheme().toLowerCase(Locale.ROOT),
                    null,
                    uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    null,
                    null,
                    null
            ).toString();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Validated Push endpoint has an invalid origin", exception);
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
            var bytes = ipv4.getAddress();
            var first = Byte.toUnsignedInt(bytes[0]);
            var second = Byte.toUnsignedInt(bytes[1]);
            var third = Byte.toUnsignedInt(bytes[2]);
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
        if (address instanceof Inet6Address ipv6) {
            var bytes = ipv6.getAddress();
            var first = Byte.toUnsignedInt(bytes[0]);
            var second = Byte.toUnsignedInt(bytes[1]);
            var third = Byte.toUnsignedInt(bytes[2]);
            var fourth = Byte.toUnsignedInt(bytes[3]);
            return (first & 0xe0) == 0x20
                    && !(first == 0x20 && second == 0x01
                    && ((third & 0xfe) == 0 || (third == 0x0d && fourth == 0xb8)))
                    && !(first == 0x20 && second == 0x02)
                    && first != 0x3f;
        }
        return false;
    }

    private static InvalidCustomerPushSubscriptionException invalid(String message) {
        return new InvalidCustomerPushSubscriptionException(message);
    }

    public record AllowedPushDestination(
            @NonNull URI uri,
            @NonNull InetAddress[] addresses,
            @NonNull String origin
    ) {

        public AllowedPushDestination {
            addresses = addresses.clone();
        }

        @Override
        public InetAddress[] addresses() {
            return addresses.clone();
        }
    }
}
