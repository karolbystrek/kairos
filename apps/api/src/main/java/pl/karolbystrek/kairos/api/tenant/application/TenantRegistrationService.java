package pl.karolbystrek.kairos.api.tenant.application;

import lombok.RequiredArgsConstructor;
import lombok.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.AccountCreationService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.location.domain.Location;
import pl.karolbystrek.kairos.api.location.domain.ManagedLocationName;
import pl.karolbystrek.kairos.api.location.infrastructure.persistence.LocationRepository;

import pl.karolbystrek.kairos.api.tenant.domain.Tenant;
import pl.karolbystrek.kairos.api.tenant.infrastructure.persistence.TenantRepository;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class TenantRegistrationService {
    private final TenantRepository tenants;
    private final AccountCreationService accounts;
    private final LocationRepository locations;
    private final Clock clock;

    @Transactional
    public StaffPrincipal register(String email, String subject, @NonNull ManagedLocationName locationName) {
        var tenant = tenants.save(Tenant.create());
        var account = accounts.createAdministrator(tenant.getId(), email, subject);
        locations.save(Location.create(tenant.getId(), locationName, clock.instant()));
        return new StaffPrincipal(account.getId(), tenant.getId(), account.getTenantRole());
    }
}
