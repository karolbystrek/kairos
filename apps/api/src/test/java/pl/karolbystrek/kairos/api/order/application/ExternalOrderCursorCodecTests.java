package pl.karolbystrek.kairos.api.order.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.karolbystrek.kairos.api.order.application.exception.InvalidOrderRequestException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalOrderCursorCodecTests {

    private final ExternalOrderCursorCodec codec = new ExternalOrderCursorCodec();

    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "!!!",
            "bm9zZXBhcmF0b3I", // "noseparator"
            "   ",
            "YQpiCmM", // "a\nb\nc"
            "MjAyNi0wMS0wMVQwMDowMDowMFoKbm90LWEtdXVpZA", // valid instant, bad UUID
            "bm90LWFuLWluc3RhbnQKMDAwMDAwMDAtMDAwMC0wMDAwLTAwMDAtMDAwMDAwMDAwMDAw" // bad instant, valid UUID
    })
    void rejectsMalformedCursors(String cursor) {
        assertThatThrownBy(() -> codec.decode(cursor)).isInstanceOf(InvalidOrderRequestException.class);
    }

    @Test
    void decodesWellFormedCursorAndTreatsNullAsFirstPage() {
        var createdAt = Instant.parse("2026-01-01T00:00:00Z");
        var id = UUID.randomUUID();

        assertThat(codec.decode(b64(createdAt + "\n" + id)))
                .isEqualTo(new ExternalOrderCursorCodec.Cursor(createdAt, id));
        assertThat(codec.decode(null)).isNull();
    }
}
