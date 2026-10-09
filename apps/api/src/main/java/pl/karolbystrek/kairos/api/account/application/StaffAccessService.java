package pl.karolbystrek.kairos.api.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffAccessContext;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.application.port.StaffLocationDirectory;
import pl.karolbystrek.kairos.api.account.domain.AccountStatus;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.LocationAssignmentRepository;
import pl.karolbystrek.kairos.api.persistence.infrastructure.DatabaseAccessContext;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StaffAccessService {

    private final DatabaseAccessContext databaseAccess;
    private final AccountRepository accountRepository;
    private final LocationAssignmentRepository assignmentRepository;
    private final StaffLocationDirectory locationDirectory;

    @Transactional(readOnly = true)
    public StaffAccessContext resolve(StaffPrincipal principal) {
        return resolve(principal, false);
    }

    @Transactional
    public StaffAccessContext resolveForUpdate(StaffPrincipal principal) {
        return resolve(principal, true);
    }

    private StaffAccessContext resolve(StaffPrincipal principal, boolean lockForUpdate) {
        if (principal == null) {
            throw new StaffAccessDeniedException("Staff authentication is required");
        }

        databaseAccess.staff(principal);
        if (lockForUpdate) databaseAccess.lockStaffLocation();
        var account = (lockForUpdate
            ? accountRepository.findForUpdateById(principal.accountId())
            : accountRepository.findById(principal.accountId()))
            .orElseThrow(() -> new StaffAccessDeniedException("The staff account is not eligible"));
        if (account.getStatus() != AccountStatus.ENABLED
            || !account.getTenantId().equals(principal.tenantId())
            || account.getTenantRole() != principal.tenantRole()) {
            throw new StaffAccessDeniedException("The staff account is not eligible");
        }

        var assignment = lockForUpdate
            ? assignmentRepository.findForUpdateByIdAccountId(account.getId())
            : assignmentRepository.findByIdAccountId(account.getId());
        if (account.getTenantRole() == TenantRole.ADMIN) {
            if (assignment.isPresent()) {
                throw new StaffAccessDeniedException("The administrator account is malformed");
            }
            return new StaffAccessContext(
                account.getId(), account.getTenantId(), account.getTenantRole(), null, null
            );
        }

        var activeAssignment = assignment
            .filter(candidate -> Objects.equals(candidate.getTenantId(), account.getTenantId()))
            .orElseThrow(() -> new StaffAccessDeniedException("A location assignment is required"));
        var location = locationDirectory.findById(activeAssignment.getLocationId())
            .filter(candidate -> candidate.tenantId().equals(account.getTenantId()))
            .filter(candidate -> candidate.isEnabled())
            .orElseThrow(() -> new StaffAccessDeniedException("An enabled assigned location is required"));

        return new StaffAccessContext(
            account.getId(),
            account.getTenantId(),
            account.getTenantRole(),
            location.id(),
            activeAssignment.getRole()
        );
    }
}
