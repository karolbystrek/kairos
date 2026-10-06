package pl.karolbystrek.kairos.api.tenant.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.AccountCreationService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.tenant.domain.Tenant;
import pl.karolbystrek.kairos.api.tenant.infrastructure.persistence.TenantRepository;
@Service
@RequiredArgsConstructor
public class TenantRegistrationService {
    private final TenantRepository tenants;
    private final AccountCreationService accounts;
    @Transactional
    public StaffPrincipal register(String email, String subject) {
        var tenant = tenants.save(Tenant.create());
        var account = accounts.createAdministrator(tenant.getId(), email, subject);
        return new StaffPrincipal(account.getId(), tenant.getId(), account.getTenantRole());
    }
}
