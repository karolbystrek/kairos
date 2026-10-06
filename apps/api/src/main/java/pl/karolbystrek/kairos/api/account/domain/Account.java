package pl.karolbystrek.kairos.api.account.domain;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "provider_subject", unique = true, length = 128)
    private String providerSubject;

    @Column(name = "authentication_cutoff")
    private Instant authenticationCutoff;

    @Enumerated(EnumType.STRING)
    @Column(name = "tenant_role", length = 32)
    private TenantRole tenantRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public static Account provisionMember(UUID tenantId, String email, String providerSubject, Instant now) {
        return provision(tenantId, email, providerSubject, TenantRole.MEMBER, now);
    }

    public static Account provisionAdministrator(UUID tenantId, String email, String providerSubject, Instant now) {
        return provision(tenantId, email, providerSubject, TenantRole.ADMIN, now);
    }

    private static Account provision(UUID tenantId, String email, String providerSubject, TenantRole role, Instant now) {
        var account = new Account();
        account.id = UUID.randomUUID();
        account.tenantId = tenantId;
        account.email = email;
        account.providerSubject = providerSubject;
        account.tenantRole = role;
        account.status = AccountStatus.ENABLED;
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    public void revokeAuthentication(Instant now) {
        authenticationCutoff = now;
    }

    public void enable(@NonNull Instant now) {
        requireNotArchived();
        changeStatus(AccountStatus.ENABLED, now);
    }

    public void disable(@NonNull Instant now) {
        requireNotArchived();
        changeStatus(AccountStatus.DISABLED, now);
    }

    public void archive(@NonNull Instant now) {
        if (isArchived()) {
            return;
        }
        status = AccountStatus.ARCHIVED;
        archivedAt = now;
        updatedAt = now;
    }

    public boolean isEnabled() {
        return status == AccountStatus.ENABLED;
    }

    public boolean isDisabled() {
        return status == AccountStatus.DISABLED;
    }

    public boolean isArchived() {
        return status == AccountStatus.ARCHIVED;
    }

    private void changeStatus(AccountStatus target, Instant now) {
        if (status == target) {
            return;
        }
        status = target;
        updatedAt = now;
    }

    private void requireNotArchived() {
        if (isArchived()) {
            throw new IllegalStateException("Archived accounts cannot be changed");
        }
    }
}
