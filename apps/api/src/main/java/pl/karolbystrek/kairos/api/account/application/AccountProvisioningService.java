package pl.karolbystrek.kairos.api.account.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountProvisioningService {

    private static final int MINIMUM_PASSWORD_LENGTH = 12;
    private static final int MAXIMUM_BCRYPT_PASSWORD_BYTES = 72;

    private final AccountRepository accountRepository;
    private final LocationAssignmentRepository assignmentRepository;
    private final StaffLocationDirectory locationDirectory;
    private final StaffAccessService staffAccessService;
    private final AccountSessionRevoker sessionRevoker;
    private final PasswordEncoder passwordEncoder;
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

        var normalizedUsername = normalizeLowercase(username, 120, "Username");
        var normalizedEmail = normalizeEmail(email);
        var normalizedDisplayName = normalizeRequired(displayName, 120, "Display name");
        validatePassword(password);
        requireAvailableIdentifiers(normalizedUsername, normalizedEmail);

        var now = clock.instant();
        var account = Account.provisionMember(
            access.tenantId(),
            normalizedUsername,
            normalizedEmail,
            passwordEncoder.encode(password),
            normalizedDisplayName,
            now
        );
        var assignment = LocationAssignment.active(
            account.getId(), location.id(), access.tenantId(), role, now
        );

        try {
            accountRepository.saveAndFlush(account);
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

    private void requireAvailableIdentifiers(String username, String email) {
        if (accountRepository.existsByUsername(username)
            || (email != null && accountRepository.existsByEmail(email))) {
            throw new AccountConflictException("An account with the supplied identity already exists");
        }
    }

    private static String normalizeRequired(String value, int maximumLength, String fieldName) {
        if (value == null) {
            throw new InvalidAccountRequestException(fieldName + " is required");
        }
        var normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new InvalidAccountRequestException(fieldName + " is required");
        }
        if (normalized.length() > maximumLength) {
            throw new InvalidAccountRequestException(fieldName + " is too long");
        }
        return normalized;
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return normalizeLowercase(email, 254, "Email");
    }

    private static String normalizeLowercase(String value, int maximumLength, String fieldName) {
        var normalized = normalizeRequired(value, Integer.MAX_VALUE, fieldName).toLowerCase(Locale.ROOT);
        if (normalized.length() > maximumLength) {
            throw new InvalidAccountRequestException(fieldName + " is too long");
        }
        return normalized;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new InvalidAccountRequestException(
                "Password must contain at least %d characters".formatted(MINIMUM_PASSWORD_LENGTH)
            );
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BCRYPT_PASSWORD_BYTES) {
            throw new InvalidAccountRequestException(
                "Password must not exceed %d UTF-8 bytes".formatted(MAXIMUM_BCRYPT_PASSWORD_BYTES)
            );
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
