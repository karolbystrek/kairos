package pl.karolbystrek.kairos.api.account.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.exception.AccountConflictException;
import pl.karolbystrek.kairos.api.account.application.exception.AccountNotFoundException;
import pl.karolbystrek.kairos.api.account.application.exception.InvalidAccountRequestException;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.ManagedAccountView;
import pl.karolbystrek.kairos.api.account.application.model.StaffAccessContext;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.application.port.AccountSessionRevoker;
import pl.karolbystrek.kairos.api.account.application.port.StaffLocationDirectory;
import pl.karolbystrek.kairos.api.account.domain.Account;
import pl.karolbystrek.kairos.api.account.domain.AccountStatus;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.LocationAssignment;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.LocationAssignmentRepository;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountProvisioningService {

    private final AccountRepository accountRepository;
    private final LocationAssignmentRepository assignmentRepository;
    private final StaffLocationDirectory locationDirectory;
    private final StaffAccessService staffAccessService;
    private final AccountCreationService accountCreationService;
    private final AccountSessionRevoker sessionRevoker;
    private final Clock clock;

    @Transactional
    public ManagedAccountView provision(
        StaffPrincipal actor,
        UUID locationId,
        String username,
        String email,
        String password,
        String displayName,
        AssignmentRole role
    ) {
        var access = staffAccessService.resolveForUpdate(actor);
        var location = locationDirectory.findById(locationId)
            .orElseThrow(() -> new AccountNotFoundException("Location was not found"));
        access.requireLocationAccess(location.tenantId(), location.id());
        requireProvisioningPermission(access, role);

        var account = accountCreationService.createMember(
            access.tenantId(),
            username,
            email,
            password,
            displayName
        );
        var assignment = LocationAssignment.active(
            account.getId(),
            location.id(),
            access.tenantId(),
            role,
            account.getCreatedAt()
        );

        try {
            assignmentRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException exception) {
            throw new AccountConflictException("An account with the supplied identity already exists", exception);
        }
        log.info(
            "Account {} provisioned {} account {} for location {}",
            access.accountId(),
            role,
            account.getId(),
            location.id()
        );

        return toView(account, assignment);
    }

    @Transactional
    public ManagedAccountView changeStatus(
        StaffPrincipal actor,
        UUID accountId,
        AccountStatus targetStatus
    ) {
        if (targetStatus == null) {
            throw new InvalidAccountRequestException("Account status is required");
        }

        var access = staffAccessService.resolveForUpdate(actor);
        var target = accountRepository.findForUpdateById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account was not found"));
        var assignment = assignmentRepository.findForUpdateByIdAccountId(accountId)
            .orElseThrow(() -> new StaffAccessDeniedException("The target account cannot be managed"));

        requireStatusManagementPermission(access, target, assignment);
        target.changeStatus(targetStatus, clock.instant());
        if (targetStatus == AccountStatus.DISABLED) {
            sessionRevoker.revokeAll(accountId);
        }
        log.info(
            "Account {} changed account {} status to {}",
            access.accountId(),
            accountId,
            targetStatus
        );

        return toView(target, assignment);
    }

    private void requireProvisioningPermission(StaffAccessContext access, AssignmentRole targetRole) {
        if (targetRole == null) {
            throw new InvalidAccountRequestException("Assignment role is required");
        }
        if (access.isTenantAdmin()) {
            return;
        }
        if (access.assignmentRole() != AssignmentRole.MANAGER || targetRole != AssignmentRole.OPERATOR) {
            throw new StaffAccessDeniedException("The account cannot provision this role");
        }
    }

    private void requireStatusManagementPermission(
        StaffAccessContext access,
        Account target,
        LocationAssignment assignment
    ) {
        if (target.getTenantRole() != TenantRole.MEMBER
            || !target.getTenantId().equals(access.tenantId())
            || !assignment.getTenantId().equals(target.getTenantId())) {
            throw new StaffAccessDeniedException("The target account cannot be managed");
        }

        if (access.isTenantAdmin()) {
            return;
        }
        if (access.assignmentRole() != AssignmentRole.MANAGER
            || assignment.getRole() != AssignmentRole.OPERATOR
            || !access.locationId().equals(assignment.getLocationId())) {
            throw new StaffAccessDeniedException("The target account cannot be managed");
        }
    }

    private static ManagedAccountView toView(Account account, LocationAssignment assignment) {
        return new ManagedAccountView(
            account.getId(),
            account.getTenantId(),
            assignment.getLocationId(),
            account.getUsername(),
            account.getEmail(),
            account.getDisplayName(),
            assignment.getRole(),
            account.getStatus(),
            account.getCreatedAt(),
            account.getUpdatedAt()
        );
    }
}
