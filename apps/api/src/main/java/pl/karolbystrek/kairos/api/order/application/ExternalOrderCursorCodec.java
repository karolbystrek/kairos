package pl.karolbystrek.kairos.api.order.application;

import lombok.NonNull;
import org.springframework.stereotype.Component;
import pl.karolbystrek.kairos.api.order.application.exception.InvalidOrderRequestException;
import pl.karolbystrek.kairos.api.order.domain.CustomerOrder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

@Component
class ExternalOrderCursorCodec {

    String encode(@NonNull CustomerOrder order) {
        var value = order.getCreatedAt() + "\n" + order.getId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    Cursor decode(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw invalidCursor();
        }

        try {
            var decoded = new String(
                    Base64.getUrlDecoder().decode(value),
                    StandardCharsets.UTF_8
            );
            var separator = decoded.indexOf('\n');
            if (separator <= 0 || separator != decoded.lastIndexOf('\n')) {
                throw invalidCursor();
            }
            return new Cursor(
                    Instant.parse(decoded.substring(0, separator)),
                    UUID.fromString(decoded.substring(separator + 1))
            );
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalidCursor();
        }
    }

    private static InvalidOrderRequestException invalidCursor() {
        return new InvalidOrderRequestException("Order cursor is invalid");
    }

    record Cursor(@NonNull Instant createdAt, @NonNull UUID orderId) {
    }
}
