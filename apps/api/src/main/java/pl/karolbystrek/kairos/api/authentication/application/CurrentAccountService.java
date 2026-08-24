package pl.karolbystrek.kairos.api.authentication.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.PanelPrincipal;
import pl.karolbystrek.kairos.api.account.application.model.PlatformOperatorPrincipal;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.AccountKind;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.authentication.application.model.CurrentAccountView;
import pl.karolbystrek.kairos.api.location.infrastructure.persistence.LocationRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CurrentAccountService {

    private final AccountRepository accountRepository;
    private final StaffAccessService staffAccessService;
    private final LocationRepository locationRepository;

    @Transactional(readOnly = true)
    public CurrentAccountView get(PanelPrincipal principal) {
        if (principal instanceof PlatformOperatorPrincipal operatorPrincipal) {
            return getOperator(operatorPrincipal);
        }
        if (!(principal instanceof StaffPrincipal staffPrincipal)) {
            throw new StaffAccessDeniedException("The panel account is not eligible");
        }

        return getTenantAccount(staffPrincipal);
    }

    private CurrentAccountView getTenantAccount(StaffPrincipal principal) {
        var access = staffAccessService.resolve(principal);
        var account = accountRepository.findById(access.accountId())
            .orElseThrow(() -> new StaffAccessDeniedException("The staff account is not eligible"));

        CurrentAccountView.LocationAssignmentView assignment = null;
        if (access.locationId() != null) {
            var location = locationRepository.findById(access.locationId())
                .orElseThrow(() -> new StaffAccessDeniedException("The assigned location is unavailable"));
            if (!location.getTenantId().equals(access.tenantId())) {
                throw new StaffAccessDeniedException("The assigned location is unavailable");
            }
            assignment = new CurrentAccountView.LocationAssignmentView(
                location.getId(),
                access.assignmentRole()
            );
        }

        var capabilities = new ArrayList<String>();
        capabilities.add("MANAGE_ORDERS");
        if (access.isTenantAdmin()) {
            capabilities.add("VIEW_TENANT_ORDERS");
            capabilities.add("MANAGE_LOCATIONS");
            capabilities.add("INVITE_MANAGERS");
            capabilities.add("INVITE_OPERATORS");
            capabilities.add("MANAGE_EXTERNAL_INTEGRATIONS");
        }
        else if (access.assignmentRole() == AssignmentRole.MANAGER) {
            capabilities.add("INVITE_OPERATORS");
        }

        return new CurrentAccountView.TenantAccountView(
            account.getId(),
            account.getUsername(),
            AccountKind.TENANT_ACCOUNT,
            account.getTenantId(),
            account.getTenantRole(),
            assignment,
            List.copyOf(capabilities)
        );
    }

    private CurrentAccountView getOperator(PlatformOperatorPrincipal principal) {
        var account = accountRepository.findById(principal.accountId())
            .orElseThrow(() -> new StaffAccessDeniedException("The panel account is not eligible"));
        if (account.getKind() != AccountKind.PLATFORM_OPERATOR || !account.isEnabled()) {
            throw new StaffAccessDeniedException("The panel account is not eligible");
        }
        return new CurrentAccountView.PlatformOperatorView(
            account.getId(),
            account.getUsername(),
            AccountKind.PLATFORM_OPERATOR,
            List.of("MANAGE_TENANT_REGISTRATION_INVITATIONS")
        );
    }
}
