package pl.karolbystrek.kairos.api.order.api.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAnOmittedLabelAndNormalizesValidCustomText() {
        var automatic = new CreateOrderRequest(null);
        var custom = new CreateOrderRequest("  Stół 7  ");

        assertThat(validator.validate(automatic)).isEmpty();
        assertThat(validator.validate(custom)).isEmpty();
        assertThat(custom.label()).isEqualTo("Stół 7");
    }

    @Test
    void rejectsBlankMultilineControlAndOverLengthLabels() {
        assertThat(validator.validate(new CreateOrderRequest("   "))).isNotEmpty();
        assertThat(validator.validate(new CreateOrderRequest("Line 1\nLine 2"))).isNotEmpty();
        assertThat(validator.validate(new CreateOrderRequest("Line 1\u2028Line 2"))).isNotEmpty();
        assertThat(validator.validate(new CreateOrderRequest("Line 1\u2029Line 2"))).isNotEmpty();
        assertThat(validator.validate(new CreateOrderRequest("Table\u00007"))).isNotEmpty();
        assertThat(validator.validate(new CreateOrderRequest("x".repeat(33)))).isNotEmpty();
    }

    @Test
    void countsUnicodeCodePointsAtTheBoundary() {
        var label = "x".repeat(31) + "🍜";

        assertThat(label).hasSize(33);
        assertThat(validator.validate(new CreateOrderRequest(label))).isEmpty();
    }
}
