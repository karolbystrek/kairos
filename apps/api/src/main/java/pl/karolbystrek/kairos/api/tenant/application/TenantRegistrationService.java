package pl.karolbystrek.kairos.api.tenant.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.AccountCreationService;
import pl.karolbystrek.kairos.api.location.domain.Location;
import pl.karolbystrek.kairos.api.location.infrastructure.persistence.LocationRepository;
import pl.karolbystrek.kairos.api.tenant.application.model.TenantRegistrationView;
import pl.karolbystrek.kairos.api.tenant.domain.Tenant;
import pl.karolbystrek.kairos.api.tenant.infrastructure.persistence.TenantRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantRegistrationService {

    private final TenantRepository tenantRepository;
    private final LocationRepository locationRepository;
    private final AccountCreationService accountCreationService;

    @Transactional
    public TenantRegistrationView register(
        String tenantName,
        String locationName,
        String administratorUsername,
        String administratorEmail,
        String administratorPassword,
        String administratorDisplayName
    ) {
        var tenant = Tenant.create(tenantName.strip());
        var location = Location.create(
            tenant.getId(),
            locationName.strip()
        );

        tenantRepository.save(tenant);
        locationRepository.save(location);
        var administrator = accountCreationService.createAdministrator(
            tenant.getId(),
            administratorUsername,
            administratorEmail,
            administratorPassword,
            administratorDisplayName
        );

        log.info(
            "Registered tenant {} with first location {} and administrator account {} ({})",
            tenant.getId(),
            location.getId(),
            administrator.getId(),
            administrator.getUsername()
        );

        return new TenantRegistrationView(
            tenant.getId(),
            location.getId(),
            administrator.getId(),
            administrator.getUsername()
        );
    }
}
