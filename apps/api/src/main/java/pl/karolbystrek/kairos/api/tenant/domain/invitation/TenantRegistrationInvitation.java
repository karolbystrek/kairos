package pl.karolbystrek.kairos.api.tenant.domain.invitation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_registration_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantRegistrationInvitation {

    private static final Duration LIFETIME = Duration.ofDays(7);

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(name = "issued_by_account_id", nullable = false)
    private UUID issuedByAccountId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TenantRegistrationInvitationState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason", length = 32)
    private TenantRegistrationInvitationRevocationReason revocationReason;

    @Column(name = "revoked_by_account_id")
    private UUID revokedByAccountId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "redeemed_tenant_id")
    private UUID redeemedTenantId;

    @Column(name = "redeemed_account_id")
    private UUID redeemedAccountId;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TenantRegistrationInvitation issue(
        @NonNull String label,
        @NonNull UUID issuedByAccountId,
        @NonNull String tokenHash,
        @NonNull Instant now
    ) {
        var invitation = new TenantRegistrationInvitation();
        invitation.id = UUID.randomUUID();
        invitation.label = label;
        invitation.issuedByAccountId = issuedByAccountId;
        invitation.tokenHash = tokenHash;
        invitation.state = TenantRegistrationInvitationState.PENDING;
        invitation.expiresAt = now.plus(LIFETIME);
        invitation.createdAt = now;
        invitation.updatedAt = now;
        return invitation;
    }

    public boolean isExpiredAt(@NonNull Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isPendingAt(@NonNull Instant now) {
        return state == TenantRegistrationInvitationState.PENDING && !isExpiredAt(now);
    }

    public void revokeByOperator(@NonNull UUID accountId, @NonNull Instant now) {
        revoke(TenantRegistrationInvitationRevocationReason.OPERATOR_REVOKED, accountId, now);
    }

    public void revokeForDisabledIssuer(@NonNull Instant now) {
        revoke(TenantRegistrationInvitationRevocationReason.ISSUER_DISABLED, null, now);
    }

    private void revoke(
        TenantRegistrationInvitationRevocationReason reason,
        UUID revokedByAccountId,
        Instant now
    ) {
        if (state != TenantRegistrationInvitationState.PENDING) {
            return;
        }
        state = TenantRegistrationInvitationState.REVOKED;
        revocationReason = reason;
        this.revokedByAccountId = revokedByAccountId;
        revokedAt = now;
        updatedAt = now;
    }

    public void redeem(@NonNull UUID tenantId, @NonNull UUID accountId, @NonNull Instant now) {
        if (!isPendingAt(now)) {
            throw new IllegalStateException("Only an effective pending invitation can be redeemed");
        }
        state = TenantRegistrationInvitationState.REDEEMED;
        redeemedTenantId = tenantId;
        redeemedAccountId = accountId;
        redeemedAt = now;
        updatedAt = now;
    }
}
