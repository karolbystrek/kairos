package pl.karolbystrek.kairos.api.authentication.application;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.authentication.infrastructure.zitadel.ZitadelClient;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffAuthenticationService {
    private final JdbcTemplate database;
    private final StaffAccessService access;

    @Transactional(readOnly = true)
    public StaffPrincipal authenticate(ZitadelClient.ProviderSession identity, Instant signedInAt) {
        var principals = database.query("SELECT * FROM public.staff_authentication(?)", (row, index) -> {
            var cutoff = row.getTimestamp("authentication_cutoff");
            if (signedInAt == null || (cutoff != null && !signedInAt.isAfter(cutoff.toInstant()))) {
                throw new StaffAccessDeniedException("Session was revoked");
            }
            return new StaffPrincipal(row.getObject("account_id", UUID.class),
                row.getObject("tenant_id", UUID.class), TenantRole.valueOf(row.getString("tenant_role")));
        }, identity.userId());
        if (principals.isEmpty()) throw new StaffAccessDeniedException("Account is unavailable");
        var principal = principals.getFirst();
        access.resolve(principal);
        return principal;
    }
}
