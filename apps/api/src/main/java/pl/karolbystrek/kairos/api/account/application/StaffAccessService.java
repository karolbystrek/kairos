package pl.karolbystrek.kairos.api.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffAccessContext;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.AccountStatus;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentStatus;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.LocationAssignmentRepository;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StaffAccessService {

    private final AccountRepository accountRepository;
    private final LocationAssignmentRepository assignmentRepository;

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

        var account = (lockForUpdate
            ? accountRepository.findForUpdateById(principal.accountId())
            : accountRepository.findById(principal.accountId()))
            .orElseThrow(() -> new StaffAccessDeniedException("The staff account is not eligible"));
        if (account.getStatus() != AccountStatus.ACTIVE
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
            .filter(candidate -> candidate.getStatus() == AssignmentStatus.ACTIVE)
            .filter(candidate -> Objects.equals(candidate.getTenantId(), account.getTenantId()))
            .orElseThrow(() -> new StaffAccessDeniedException("An active location assignment is required"));

        return new StaffAccessContext(
            account.getId(),
            account.getTenantId(),
            account.getTenantRole(),
            activeAssignment.getLocationId(),
            activeAssignment.getRole()
        );
    }
}
