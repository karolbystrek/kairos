package pl.karolbystrek.kairos.api.tenant.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.AccountCreationService;
import pl.karolbystrek.kairos.api.tenant.application.model.TenantRegistrationView;
import pl.karolbystrek.kairos.api.tenant.domain.Tenant;
import pl.karolbystrek.kairos.api.tenant.infrastructure.persistence.TenantRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantRegistrationService {

    private final TenantRepository tenantRepository;
    private final AccountCreationService accountCreationService;

    @Transactional
    public TenantRegistrationView register(
        String administratorUsername,
        String administratorEmail,
        String administratorPassword
    ) {
        var tenant = Tenant.create();

        tenantRepository.save(tenant);
        var administrator = accountCreationService.createAdministrator(
            tenant.getId(),
            administratorUsername,
            administratorEmail,
            administratorPassword
        );

        log.info(
            "Registered tenant {} with administrator account {} ({})",
            tenant.getId(),
            administrator.getId(),
            administrator.getUsername()
        );

        return new TenantRegistrationView(
            tenant.getId(),
            administrator.getId(),
            administrator.getUsername()
        );
    }
}
