package pl.karolbystrek.kairos.api.tenant.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.AccountCreationService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantRegistrationService {
    private final JdbcTemplate database;
    private final Clock clock;

    @Transactional
    public StaffPrincipal register(String email, String subject) {
        try {
            return database.queryForObject("SELECT * FROM public.register_tenant(?, ?, ?)",
                (row, index) -> new StaffPrincipal(row.getObject("account_id", UUID.class),
                    row.getObject("tenant_id", UUID.class), TenantRole.valueOf(row.getString("tenant_role"))),
                email, subject, Timestamp.from(clock.instant()));
        } catch (DataAccessException exception) {
            throw AccountCreationService.bootstrapFailure(exception);
        }
    }
}
