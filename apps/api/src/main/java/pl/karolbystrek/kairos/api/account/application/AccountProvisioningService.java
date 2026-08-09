package pl.karolbystrek.kairos.api.account.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.exception.AccountNotFoundException;
import pl.karolbystrek.kairos.api.account.application.exception.InvalidAccountRequestException;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.ManagedAccountView;
import pl.karolbystrek.kairos.api.account.application.model.StaffAccessContext;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.application.port.AccountSessionRevoker;
import pl.karolbystrek.kairos.api.account.domain.Account;
import pl.karolbystrek.kairos.api.account.domain.AccountStatus;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.LocationAssignment;
import pl.karolbystrek.kairos.api.account.domain.invitation.AccountInvitationRevocationReason;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountAuthenticatorRepository;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.LocationAssignmentRepository;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountProvisioningService {

    private final AccountRepository accountRepository;
    private final LocationAssignmentRepository assignmentRepository;
    private final AccountAuthenticatorRepository authenticatorRepository;
    private final StaffAccessService staffAccessService;
    private final AccountInvitationService invitationService;
    private final AccountSessionRevoker sessionRevoker;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ManagedAccountView> listManageable(StaffPrincipal actor) {
        var access = staffAccessService.resolve(actor);
        if (!access.isTenantAdmin() && access.assignmentRole() != AssignmentRole.MANAGER) {
            throw new StaffAccessDeniedException("The account cannot list managed accounts");
        }

        var assignments = access.isTenantAdmin()
            ? assignmentRepository.findAllByTenantId(access.tenantId())
            : assignmentRepository.findAllByTenantIdAndIdLocationIdAndRole(
                access.tenantId(),
                access.locationId(),
                AssignmentRole.OPERATOR
            );
        var accountsById = accountRepository.findAllById(
                assignments.stream().map(LocationAssignment::getAccountId).toList()
            ).stream()
            .collect(Collectors.toMap(Account::getId, Function.identity()));

        return assignments.stream()
            .filter(assignment -> !requireManagedAccount(
                accountsById.get(assignment.getAccountId()),
                access
            ).isArchived())
            .map(assignment -> toView(
                requireManagedAccount(accountsById.get(assignment.getAccountId()), access),
                assignment
            ))
            .sorted(Comparator.comparing(ManagedAccountView::username))
            .toList();
    }

    @Transactional
    public ManagedAccountView updateStatus(
        StaffPrincipal actor,
        UUID accountId,
        AccountStatus targetStatus
    ) {
        if (targetStatus == null) {
            throw new InvalidAccountRequestException("Account status is required");
        }
        if (targetStatus == AccountStatus.ARCHIVED) {
            throw new InvalidAccountRequestException("Account archival requires Delete");
        }

        var access = staffAccessService.resolveForUpdate(actor);
        var target = accountRepository.findForUpdateById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account was not found"));
        var assignment = assignmentRepository.findForUpdateByIdAccountId(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account was not found"));

        requireStatusManagementPermission(access, target, assignment);
        if (target.isArchived()) {
            throw new AccountNotFoundException("Account was not found");
        }
        var now = clock.instant();
        switch (targetStatus) {
            case ENABLED -> target.enable(now);
            case DISABLED -> target.disable(now);
            case ARCHIVED -> throw new InvalidAccountRequestException(
                "Account archival requires Delete"
            );
        }
        if (targetStatus == AccountStatus.DISABLED) {
            sessionRevoker.revokeAll(accountId);
            invitationService.revokePendingByIssuer(accountId);
        }
        log.info(
            "Account {} changed account {} status to {}",
            access.accountId(),
            accountId,
            targetStatus
        );

        return toView(target, assignment);
    }

    @Transactional
    public void delete(StaffPrincipal actor, UUID accountId) {
        var access = staffAccessService.resolveForUpdate(actor);
        var target = accountRepository.findForUpdateById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account was not found"));
        var assignment = assignmentRepository.findForUpdateByIdAccountId(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account was not found"));
        requireStatusManagementPermission(access, target, assignment);
        if (target.isArchived()) {
            return;
        }

        target.archive(clock.instant());
        authenticatorRepository.deleteExternalIdentities(Set.of(accountId));
        sessionRevoker.revokeAll(accountId);
        invitationService.revokePendingByIssuer(
            accountId,
            AccountInvitationRevocationReason.ISSUER_ARCHIVED
        );
        accountRepository.flush();
        log.info("Account {} deleted account {}", access.accountId(), accountId);
    }

    private void requireStatusManagementPermission(
        StaffAccessContext access,
        Account target,
        LocationAssignment assignment
    ) {
        if (target.getTenantRole() != TenantRole.MEMBER
            || !target.getTenantId().equals(access.tenantId())
            || !assignment.getTenantId().equals(target.getTenantId())) {
            throw new AccountNotFoundException("Account was not found");
        }

        if (access.isTenantAdmin()) {
            return;
        }
        if (access.assignmentRole() != AssignmentRole.MANAGER
            || assignment.getRole() != AssignmentRole.OPERATOR
            || !access.locationId().equals(assignment.getLocationId())) {
            throw new AccountNotFoundException("Account was not found");
        }
    }

    private static ManagedAccountView toView(Account account, LocationAssignment assignment) {
        return new ManagedAccountView(
            account.getId(),
            account.getTenantId(),
            assignment.getLocationId(),
            account.getUsername(),
            account.getEmail(),
            assignment.getRole(),
            account.getStatus(),
            account.getCreatedAt(),
            account.getUpdatedAt()
        );
    }

    private static Account requireManagedAccount(Account account, StaffAccessContext access) {
        if (account == null
            || account.getTenantRole() != TenantRole.MEMBER
            || !account.getTenantId().equals(access.tenantId())) {
            throw new StaffAccessDeniedException("The managed account data is inconsistent");
        }
        return account;
    }
}
