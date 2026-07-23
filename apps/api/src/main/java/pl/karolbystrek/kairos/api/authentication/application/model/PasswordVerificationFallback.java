package pl.karolbystrek.kairos.api.authentication.application.model;

import lombok.NonNull;

public record PasswordVerificationFallback(
    @NonNull String candidate,
    @NonNull String encodedCandidate
) {
}
