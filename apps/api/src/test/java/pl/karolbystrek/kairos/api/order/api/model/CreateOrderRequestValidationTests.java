package pl.karolbystrek.kairos.api.order.api.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestValidationTests {

    private static final UUID LOCATION_ID = UUID.randomUUID();

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAnOmittedLabelAndNormalizesValidCustomText() {
        var automatic = new CreateOrderRequest(LOCATION_ID, null);
        var custom = new CreateOrderRequest(LOCATION_ID, "  Stół 7  ");

        assertThat(validator.validate(automatic)).isEmpty();
        assertThat(validator.validate(custom)).isEmpty();
        assertThat(custom.label()).isEqualTo("Stół 7");
    }

    @Test
    void rejectsBlankMultilineControlAndOverLengthLabels() {
        assertThat(validator.validate(new CreateOrderRequest(LOCATION_ID, "   "))).isNotEmpty();
        assertThat(validator.validate(new CreateOrderRequest(LOCATION_ID, "Line 1\nLine 2"))).isNotEmpty();
        assertThat(validator.validate(new CreateOrderRequest(LOCATION_ID, "Line 1\u2028Line 2"))).isNotEmpty();
        assertThat(validator.validate(new CreateOrderRequest(LOCATION_ID, "Line 1\u2029Line 2"))).isNotEmpty();
        assertThat(validator.validate(new CreateOrderRequest(LOCATION_ID, "Table\u00007"))).isNotEmpty();
        assertThat(validator.validate(new CreateOrderRequest(LOCATION_ID, "x".repeat(33)))).isNotEmpty();
    }

    @Test
    void countsUnicodeCodePointsAtTheBoundary() {
        var label = "x".repeat(31) + "🍜";

        assertThat(label).hasSize(33);
        assertThat(validator.validate(new CreateOrderRequest(LOCATION_ID, label))).isEmpty();
    }
}
