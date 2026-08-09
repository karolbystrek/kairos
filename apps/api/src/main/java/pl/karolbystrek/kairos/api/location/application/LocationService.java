package pl.karolbystrek.kairos.api.location.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.AccountInvitationService;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffAccessContext;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.application.port.AccountSessionRevoker;
import pl.karolbystrek.kairos.api.account.domain.Account;
import pl.karolbystrek.kairos.api.account.domain.invitation.AccountInvitationRevocationReason;
import pl.karolbystrek.kairos.api.account.domain.assignment.LocationAssignment;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountAuthenticatorRepository;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.AccountRepository;
import pl.karolbystrek.kairos.api.account.infrastructure.persistence.LocationAssignmentRepository;
import pl.karolbystrek.kairos.api.integration.infrastructure.persistence.ApiKeyRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookSubscriptionLocationAccessRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookSubscriptionRepository;
import pl.karolbystrek.kairos.api.location.application.exception.InvalidLocationRequestException;
import pl.karolbystrek.kairos.api.location.application.exception.LocationConflictException;
import pl.karolbystrek.kairos.api.location.application.exception.LocationNotFoundException;
import pl.karolbystrek.kairos.api.location.application.model.LocationView;
import pl.karolbystrek.kairos.api.location.domain.Location;
import pl.karolbystrek.kairos.api.location.domain.LocationStatus;
import pl.karolbystrek.kairos.api.location.domain.ManagedLocationName;
import pl.karolbystrek.kairos.api.location.infrastructure.persistence.LocationRepository;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;
import pl.karolbystrek.kairos.api.tenant.infrastructure.persistence.TenantRepository;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final LocationRepository locationRepository;
    private final StaffAccessService staffAccessService;
    private final TenantRepository tenantRepository;
    private final LocationAssignmentRepository assignmentRepository;
    private final AccountRepository accountRepository;
    private final AccountAuthenticatorRepository authenticatorRepository;
    private final AccountInvitationService invitationService;
    private final AccountSessionRevoker sessionRevoker;
    private final CustomerOrderRepository orderRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookSubscriptionLocationAccessRepository subscriptionLocationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<LocationView> listAccessible(StaffPrincipal principal) {
        var access = staffAccessService.resolve(principal);
        var locations = access.isTenantAdmin()
                ? locationRepository.findAllByTenantIdAndStatusNot(
                    access.tenantId(),
                    LocationStatus.ARCHIVED
                )
                : List.of(requireAssignedLocation(access));

        return locations.stream()
                .sorted(locationComparator())
                .map(LocationView::from)
                .toList();
    }

    @Transactional
    public LocationView create(StaffPrincipal principal, String candidateName) {
        var access = requireAdministrator(principal, true);
        tenantRepository.findForUpdateById(access.tenantId())
            .orElseThrow(LocationNotFoundException::new);
        var name = parseName(candidateName);
        requireNameAvailable(access.tenantId(), name, null);
        var location = Location.create(access.tenantId(), name, clock.instant());
        try {
            locationRepository.saveAndFlush(location);
        }
        catch (DataIntegrityViolationException exception) {
            throw nameConflict(exception);
        }
        log.info("Account {} created location {}", access.accountId(), location.getId());
        return LocationView.from(location);
    }

    @Transactional
    public LocationView rename(
        StaffPrincipal principal,
        UUID locationId,
        String candidateName
    ) {
        var access = requireAdministrator(principal, true);
        var location = requireMutableLocationForUpdate(locationId, access.tenantId());
        var name = parseName(candidateName);
        requireNameAvailable(access.tenantId(), name, location.getId());
        location.rename(name, clock.instant());
        try {
            locationRepository.flush();
        }
        catch (DataIntegrityViolationException exception) {
            throw nameConflict(exception);
        }
        log.info("Account {} renamed location {}", access.accountId(), location.getId());
        return LocationView.from(location);
    }

    @Transactional
    public LocationView updateStatus(
        StaffPrincipal principal,
        UUID locationId,
        LocationStatus target
    ) {
        if (target == null) {
            throw new InvalidLocationRequestException("Location status is required");
        }
        if (target == LocationStatus.ARCHIVED) {
            throw new InvalidLocationRequestException("Location archival requires Delete");
        }
        var access = requireAdministrator(principal, true);
        var location = requireMutableLocationForUpdate(locationId, access.tenantId());
        if (location.getStatus() == target) {
            return LocationView.from(location);
        }

        var now = clock.instant();
        var accounts = assignedAccountsForUpdate(location);
        if (target == LocationStatus.DISABLED) {
            if (orderRepository.existsByLocationIdAndStatusIn(
                location.getId(),
                OrderStatus.activeStatuses()
            )) {
                throw new LocationConflictException(
                    LocationConflictException.Reason.ACTIVE_ORDERS,
                    "Complete or cancel every active order before disabling this location"
                );
            }
            location.disable(now);
            accounts.stream()
                .filter(account -> !account.isArchived())
                .forEach(account -> account.disable(now));
            var accountIds = accounts.stream()
                .filter(account -> !account.isArchived())
                .map(Account::getId)
                .toList();
            sessionRevoker.revokeAll(accountIds);
            invitationService.revokePendingByLocation(
                location.getId(),
                AccountInvitationRevocationReason.LOCATION_DISABLED
            );
        }
        else {
            location.enable(now);
            accounts.stream()
                .filter(account -> !account.isArchived())
                .forEach(account -> account.enable(now));
        }
        accountRepository.flush();
        locationRepository.flush();
        log.info(
            "Account {} changed location {} status to {}",
            access.accountId(),
            location.getId(),
            target
        );
        return LocationView.from(location);
    }

    @Transactional
    public void delete(StaffPrincipal principal, UUID locationId) {
        var access = requireAdministrator(principal, true);
        var location = locationRepository.findForUpdateById(locationId)
            .filter(candidate -> candidate.getTenantId().equals(access.tenantId()))
            .orElseThrow(LocationNotFoundException::new);
        if (location.isArchived()) {
            return;
        }
        if (!location.isDisabled()) {
            throw new LocationConflictException(
                LocationConflictException.Reason.ENABLED_DELETE,
                "Disable the location before deleting it"
            );
        }

        var now = clock.instant();
        var accounts = assignedAccountsForUpdate(location).stream()
            .filter(account -> !account.isArchived())
            .toList();
        var accountIds = accounts.stream().map(Account::getId).toList();
        accounts.forEach(account -> account.archive(now));
        authenticatorRepository.deleteExternalIdentities(accountIds);
        sessionRevoker.revokeAll(accountIds);
        invitationService.revokePendingByLocation(
            location.getId(),
            AccountInvitationRevocationReason.LOCATION_ARCHIVED
        );
        removeIntegrationSelections(location, now);
        location.archive(now);
        accountRepository.flush();
        locationRepository.flush();
        log.info("Account {} deleted location {}", access.accountId(), location.getId());
    }

    private Location requireAssignedLocation(StaffAccessContext access) {
        var location = locationRepository.findById(access.locationId())
                .orElseThrow(() -> new StaffAccessDeniedException("The assigned location is not available"));
        access.requireLocationAccess(location.getTenantId(), location.getId());
        if (!location.isEnabled()) {
            throw new StaffAccessDeniedException("The assigned location is not available");
        }
        return location;
    }

    private StaffAccessContext requireAdministrator(
        StaffPrincipal principal,
        boolean lockForUpdate
    ) {
        var access = lockForUpdate
            ? staffAccessService.resolveForUpdate(principal)
            : staffAccessService.resolve(principal);
        if (!access.isTenantAdmin()) {
            throw new StaffAccessDeniedException("Only a tenant administrator can manage locations");
        }
        return access;
    }

    private Location requireMutableLocationForUpdate(UUID locationId, UUID tenantId) {
        return locationRepository.findForUpdateById(locationId)
            .filter(candidate -> candidate.getTenantId().equals(tenantId))
            .filter(candidate -> !candidate.isArchived())
            .orElseThrow(LocationNotFoundException::new);
    }

    private List<Account> assignedAccountsForUpdate(Location location) {
        var assignments = assignmentRepository.findAllForUpdateByTenantIdAndIdLocationId(
            location.getTenantId(),
            location.getId()
        );
        var accountIds = assignments.stream()
            .map(LocationAssignment::getAccountId)
            .toList();
        if (accountIds.isEmpty()) {
            return List.of();
        }
        return accountRepository.findAllForUpdateByIdIn(accountIds);
    }

    private void removeIntegrationSelections(Location location, java.time.Instant now) {
        var apiKeys = apiKeyRepository.findAllForUpdateByTenantIdAndLocationId(
            location.getTenantId(),
            location.getId()
        );
        apiKeys.forEach(apiKey -> apiKey.removeLocationAccess(location.getId(), now));
        apiKeyRepository.flush();

        var accesses = subscriptionLocationRepository.findAllForUpdateByLocationId(
            location.getId()
        );
        var subscriptionIds = accesses.stream()
            .map(access -> access.getSubscriptionId())
            .collect(Collectors.toSet());
        if (subscriptionIds.isEmpty()) {
            return;
        }
        var subscriptions = subscriptionRepository.findAllForUpdateByTenantIdAndIdIn(
            location.getTenantId(),
            subscriptionIds
        );
        subscriptionLocationRepository.deleteAllByLocationId(location.getId());
        subscriptionLocationRepository.flush();
        subscriptions.stream()
            .filter(subscription -> !subscriptionLocationRepository.existsBySubscriptionId(
                subscription.getId()
            ))
            .forEach(subscription -> subscription.archive(now));
        subscriptionRepository.flush();
    }

    private void requireNameAvailable(
        UUID tenantId,
        ManagedLocationName name,
        UUID excludedLocationId
    ) {
        var unavailable = excludedLocationId == null
            ? locationRepository.existsByTenantIdAndNormalizedNameAndStatusNot(
                tenantId,
                name.normalizedValue(),
                LocationStatus.ARCHIVED
            )
            : locationRepository.existsByTenantIdAndNormalizedNameAndStatusNotAndIdNot(
                tenantId,
                name.normalizedValue(),
                LocationStatus.ARCHIVED,
                excludedLocationId
            );
        if (unavailable) {
            throw nameConflict(null);
        }
    }

    private static ManagedLocationName parseName(String candidate) {
        try {
            return ManagedLocationName.from(candidate);
        }
        catch (IllegalArgumentException exception) {
            throw new InvalidLocationRequestException(exception.getMessage(), exception);
        }
    }

    private static LocationConflictException nameConflict(Throwable cause) {
        return new LocationConflictException(
            LocationConflictException.Reason.NAME_CONFLICT,
            "A location with that name already exists",
            cause
        );
    }

    private static Comparator<Location> locationComparator() {
        return Comparator
            .comparingInt((Location location) -> location.isEnabled() ? 0 : 1)
            .thenComparing(Location::getNormalizedName)
            .thenComparing(Location::getId);
    }
}
