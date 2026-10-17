package pl.karolbystrek.kairos.api.order.api.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestValidationTests {

    private static final UUID LOCATION_ID = UUID.randomUUID();

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAnOmittedOrBlankLabelAndNormalizesValidText() {
        var omitted = new CreateOrderRequest(LOCATION_ID, null, null);
        var blank = new CreateOrderRequest(LOCATION_ID, "   ", null);
        var custom = new CreateOrderRequest(LOCATION_ID, "  Stół 7  ", null);

        assertThat(validator.validate(omitted)).isEmpty();
        assertThat(validator.validate(blank)).isEmpty();
        assertThat(blank.label()).isNull();
        assertThat(validator.validate(custom)).isEmpty();
        assertThat(custom.label()).isEqualTo("Stół 7");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Line 1\nLine 2", "Line 1\u2028Line 2", "Line 1\u2029Line 2", "Table\u00007",
            "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void rejectsMultilineControlAndOverLengthLabels(String label) {
        assertThat(validator.validate(new CreateOrderRequest(LOCATION_ID, label, null))).isNotEmpty();
    }

    @Test
    void countsUnicodeCodePointsAtTheBoundary() {
        var label = "x".repeat(79) + "🍜";

        assertThat(label).hasSize(81);
        assertThat(validator.validate(new CreateOrderRequest(LOCATION_ID, label, null))).isEmpty();
    }
}
