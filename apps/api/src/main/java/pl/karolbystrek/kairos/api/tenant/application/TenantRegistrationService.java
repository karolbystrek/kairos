package pl.karolbystrek.kairos.api.tenant.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.AccountCreationService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.Account;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;
import pl.karolbystrek.kairos.api.tenant.domain.Tenant;
import pl.karolbystrek.kairos.api.tenant.infrastructure.persistence.TenantRepository;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class TenantRegistrationService {
    private final TenantRepository tenants;
    private final AccountCreationService accounts;
    private final DatabaseAccessContext databaseAccess;
    private final Clock clock;

    @Transactional
    public StaffPrincipal register(String email, String subject) {
        var tenant = Tenant.create();
        var account = Account.provisionAdministrator(tenant.getId(), email, subject, clock.instant());
        databaseAccess.registration(tenant.getId(), account.getId());
        tenants.saveAndFlush(tenant);
        accounts.create(account);
        return new StaffPrincipal(account.getId(), account.getTenantId(), account.getTenantRole());
    }
}
