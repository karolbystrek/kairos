package pl.karolbystrek.kairos.api.integration.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.model.StaffAccessContext;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationAccessDeniedException;

@Service
@RequiredArgsConstructor
public class IntegrationAdministrationAccessService {

    private final StaffAccessService staffAccessService;

    public StaffAccessContext requireAdministrator(StaffPrincipal principal) {
        return requireAdministrator(staffAccessService.resolve(principal));
    }

    public StaffAccessContext requireAdministratorForUpdate(StaffPrincipal principal) {
        return requireAdministrator(staffAccessService.resolveForUpdate(principal));
    }

    private static StaffAccessContext requireAdministrator(StaffAccessContext access) {
        if (!access.isTenantAdmin()) {
            throw new IntegrationAccessDeniedException(
                    "External Integration management requires a tenant administrator"
            );
        }
        return access;
    }
}
