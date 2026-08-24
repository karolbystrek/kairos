package pl.karolbystrek.kairos.api.tenant.api.model;

import pl.karolbystrek.kairos.api.tenant.application.model.TenantRegistrationInvitationPreview;

import java.time.Instant;

public record TenantRegistrationInvitationPreviewResponse(Instant expiresAt) {

    public static TenantRegistrationInvitationPreviewResponse from(
        TenantRegistrationInvitationPreview preview
    ) {
        return new TenantRegistrationInvitationPreviewResponse(preview.expiresAt());
    }
}
