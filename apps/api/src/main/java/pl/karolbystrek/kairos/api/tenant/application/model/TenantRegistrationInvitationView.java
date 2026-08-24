package pl.karolbystrek.kairos.api.tenant.application.model;

import java.time.Instant;
import java.util.UUID;

public record TenantRegistrationInvitationView(
    UUID id,
    String label,
    String issuedByUsername,
    Instant createdAt,
    Instant expiresAt
) {
}
