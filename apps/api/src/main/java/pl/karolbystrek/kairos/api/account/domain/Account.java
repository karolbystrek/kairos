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

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, unique = true, length = 120)
    private String username;

    @Column(unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "tenant_role", nullable = false, length = 32)
    private TenantRole tenantRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Account provisionMember(
        @NonNull UUID tenantId,
        @NonNull String username,
        String email,
        @NonNull String passwordHash,
        @NonNull String displayName,
        @NonNull Instant now
    ) {
        var account = new Account();
        account.id = UUID.randomUUID();
        account.tenantId = tenantId;
        account.username = username;
        account.email = email;
        account.passwordHash = passwordHash;
        account.displayName = displayName;
        account.tenantRole = TenantRole.MEMBER;
        account.status = AccountStatus.ACTIVE;
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    public void changeStatus(@NonNull AccountStatus target, @NonNull Instant now) {
        status = target;
        updatedAt = now;
    }
}
