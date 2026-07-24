package pl.karolbystrek.kairos.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

final class Utf8SizeValidator implements ConstraintValidator<Utf8Size, CharSequence> {

    private int maximumBytes;

    @Override
    public void initialize(Utf8Size constraint) {
        maximumBytes = constraint.max();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null
            || value.toString().getBytes(StandardCharsets.UTF_8).length <= maximumBytes;
    }
}
