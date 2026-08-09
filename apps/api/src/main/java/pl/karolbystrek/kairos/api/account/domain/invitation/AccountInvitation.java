package pl.karolbystrek.kairos.api.account.domain.invitation;

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
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountInvitation {

    private static final Duration LIFETIME = Duration.ofDays(7);

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "issued_by_account_id", nullable = false)
    private UUID issuedByAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_role", nullable = false, length = 32)
    private AssignmentRole assignmentRole;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountInvitationState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason", length = 32)
    private AccountInvitationRevocationReason revocationReason;

    @Column(name = "revoked_at")
    private Instant revokedAt;

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

    public static AccountInvitation issue(
        @NonNull UUID tenantId,
        @NonNull UUID locationId,
        @NonNull UUID issuedByAccountId,
        @NonNull AssignmentRole assignmentRole,
        @NonNull String tokenHash,
        @NonNull Instant now
    ) {
        var invitation = new AccountInvitation();
        invitation.id = UUID.randomUUID();
        invitation.tenantId = tenantId;
        invitation.locationId = locationId;
        invitation.issuedByAccountId = issuedByAccountId;
        invitation.assignmentRole = assignmentRole;
        invitation.tokenHash = tokenHash;
        invitation.state = AccountInvitationState.PENDING;
        invitation.expiresAt = now.plus(LIFETIME);
        invitation.createdAt = now;
        invitation.updatedAt = now;
        return invitation;
    }

    public boolean isExpiredAt(@NonNull Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isPendingAt(@NonNull Instant now) {
        return state == AccountInvitationState.PENDING && !isExpiredAt(now);
    }

    public void revoke(
        @NonNull AccountInvitationRevocationReason reason,
        @NonNull Instant now
    ) {
        if (state != AccountInvitationState.PENDING) {
            return;
        }
        state = AccountInvitationState.REVOKED;
        revocationReason = reason;
        revokedAt = now;
        updatedAt = now;
    }

    public void redeem(@NonNull UUID accountId, @NonNull Instant now) {
        if (!isPendingAt(now)) {
            throw new IllegalStateException("Only an effective pending invitation can be redeemed");
        }
        state = AccountInvitationState.REDEEMED;
        redeemedAccountId = accountId;
        redeemedAt = now;
        updatedAt = now;
    }
}
