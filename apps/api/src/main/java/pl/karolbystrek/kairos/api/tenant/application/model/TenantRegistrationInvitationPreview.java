package pl.karolbystrek.kairos.api.tenant.application.model;

import java.time.Instant;

public record TenantRegistrationInvitationPreview(Instant expiresAt) {
}
