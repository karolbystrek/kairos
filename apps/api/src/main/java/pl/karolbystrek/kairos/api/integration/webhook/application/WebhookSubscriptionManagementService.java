package pl.karolbystrek.kairos.api.integration.webhook.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.integration.application.IntegrationAdministrationAccessService;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationConflictException;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationNotFoundException;
import pl.karolbystrek.kairos.api.integration.application.exception.InvalidIntegrationRequestException;
import pl.karolbystrek.kairos.api.integration.domain.ExternalIntegration;
import pl.karolbystrek.kairos.api.integration.domain.ManagedIntegrationName;
import pl.karolbystrek.kairos.api.integration.infrastructure.persistence.ExternalIntegrationRepository;
import pl.karolbystrek.kairos.api.integration.webhook.application.model.IssuedWebhookSigningSecretView;
import pl.karolbystrek.kairos.api.integration.webhook.application.model.IssuedWebhookSubscriptionView;
import pl.karolbystrek.kairos.api.integration.webhook.application.model.WebhookSigningSecretVersionView;
import pl.karolbystrek.kairos.api.integration.webhook.application.model.WebhookSubscriptionView;
import pl.karolbystrek.kairos.api.order.domain.OrderEventType;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSigningSecretVersion;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscription;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscriptionEventSelection;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscriptionLocationAccess;
import pl.karolbystrek.kairos.api.integration.webhook.domain.WebhookSubscriptionStatus;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.config.WebhookProperties;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http.InvalidWebhookDestinationException;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.http.WebhookDestinationPolicy;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookSigningSecretVersionRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookSubscriptionEventSelectionRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookSubscriptionLocationAccessRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.persistence.WebhookSubscriptionRepository;
import pl.karolbystrek.kairos.api.integration.webhook.infrastructure.security.SigningSecretCipher;
import pl.karolbystrek.kairos.api.location.infrastructure.persistence.LocationRepository;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WebhookSubscriptionManagementService {

    private static final int MAXIMUM_DESTINATION_LENGTH = 2048;

    private final IntegrationAdministrationAccessService administrationAccessService;
    private final ExternalIntegrationRepository integrationRepository;
    private final LocationRepository locationRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookSubscriptionLocationAccessRepository locationAccessRepository;
    private final WebhookSubscriptionEventSelectionRepository eventSelectionRepository;
    private final WebhookSigningSecretVersionRepository signingSecretRepository;
    private final WebhookDestinationPolicy destinationPolicy;
    private final SigningSecretCipher signingSecretCipher;
    private final WebhookProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<WebhookSubscriptionView> list(StaffPrincipal principal, UUID integrationId) {
        var access = administrationAccessService.requireAdministrator(principal);
        requireIntegration(integrationId, access.tenantId(), false);
        var subscriptions = subscriptionRepository
                .findAllByIntegrationIdAndArchivedAtIsNullOrderByCreatedAt(integrationId);
        return toViews(subscriptions);
    }

    @Transactional
    public IssuedWebhookSubscriptionView create(
            StaffPrincipal principal,
            UUID integrationId,
            String candidateName,
            String candidateDestination,
            Set<UUID> locationIds,
            Set<OrderEventType> eventTypes
    ) {
        var access = administrationAccessService.requireAdministratorForUpdate(principal);
        requireIntegration(integrationId, access.tenantId(), true);
        var name = parseName(candidateName);
        var destination = parseDestination(candidateDestination);
        validateSelections(access.tenantId(), locationIds, eventTypes);
        if (subscriptionRepository.existsByIntegrationIdAndNormalizedName(
                integrationId,
                name.normalizedValue()
        )) {
            throw new IntegrationConflictException(
                    "A webhook subscription with this name already exists"
            );
        }

        var now = clock.instant();
        var subscription = WebhookSubscription.create(
                integrationId,
                access.tenantId(),
                name.value(),
                name.normalizedValue(),
                destination,
                now
        );
        try {
            subscriptionRepository.saveAndFlush(subscription);
            replaceSelections(subscription, locationIds, eventTypes);
            var issued = issueSigningSecret(subscription.getId(), now);
            return new IssuedWebhookSubscriptionView(toView(subscription), issued.signingSecret());
        } catch (DataIntegrityViolationException exception) {
            throw new IntegrationConflictException(
                    "A webhook subscription with this name already exists",
                    exception
            );
        }
    }

    @Transactional
    public WebhookSubscriptionView update(
            StaffPrincipal principal,
            UUID subscriptionId,
            String candidateName,
            String candidateDestination,
            Set<UUID> locationIds,
            Set<OrderEventType> eventTypes
    ) {
        var access = administrationAccessService.requireAdministratorForUpdate(principal);
        var subscription = requireSubscriptionForUpdate(subscriptionId, access.tenantId());
        requireIntegration(subscription.getIntegrationId(), access.tenantId(), true);
        var name = parseName(candidateName);
        var destination = parseDestination(candidateDestination);
        validateSelections(access.tenantId(), locationIds, eventTypes);
        if (subscriptionRepository.existsByIntegrationIdAndNormalizedNameAndIdNot(
                subscription.getIntegrationId(),
                name.normalizedValue(),
                subscription.getId()
        )) {
            throw new IntegrationConflictException(
                    "A webhook subscription with this name already exists"
            );
        }

        subscription.reconfigure(name.value(), name.normalizedValue(), destination, clock.instant());
        replaceSelections(subscription, locationIds, eventTypes);
        try {
            subscriptionRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new IntegrationConflictException(
                    "A webhook subscription with this name already exists",
                    exception
            );
        }
        return toView(subscription);
    }

    @Transactional
    public WebhookSubscriptionView changeStatus(
            StaffPrincipal principal,
            UUID subscriptionId,
            WebhookSubscriptionStatus target
    ) {
        var access = administrationAccessService.requireAdministratorForUpdate(principal);
        var subscription = requireSubscriptionForUpdate(subscriptionId, access.tenantId());
        requireIntegration(subscription.getIntegrationId(), access.tenantId(), true);
        var now = clock.instant();
        switch (target) {
            case ENABLED -> {
                requireConfigured(subscription.getId());
                subscription.enable(now);
            }
            case DISABLED -> subscription.disable(now);
            case ARCHIVED -> throw new InvalidIntegrationRequestException(
                    "Archive a webhook subscription through its removal operation"
            );
        }
        return toView(subscription);
    }

    @Transactional
    public void archive(StaffPrincipal principal, UUID subscriptionId) {
        var access = administrationAccessService.requireAdministratorForUpdate(principal);
        var subscription = requireSubscriptionForUpdate(subscriptionId, access.tenantId());
        requireIntegration(subscription.getIntegrationId(), access.tenantId(), true);
        subscription.archive(clock.instant());
    }

    @Transactional
    public IssuedWebhookSigningSecretView rotateSigningSecret(
            StaffPrincipal principal,
            UUID subscriptionId
    ) {
        var access = administrationAccessService.requireAdministratorForUpdate(principal);
        var subscription = requireSubscriptionForUpdate(subscriptionId, access.tenantId());
        requireIntegration(subscription.getIntegrationId(), access.tenantId(), true);
        var now = clock.instant();
        var versions = signingSecretRepository
                .findAllForUpdateBySubscriptionIdOrderByIssuedAtDesc(subscriptionId);
        var current = versions.stream()
                .filter(WebhookSigningSecretVersion::isCurrent)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Webhook subscription has no current signing secret"
                ));
        versions.stream()
                .filter(version -> version != current)
                .filter(version -> version.getRetiredAt() == null)
                .forEach(version -> version.retire(now));
        current.beginOverlap(now.plus(properties.signing().rotationOverlap()));
        signingSecretRepository.flush();
        return issueSigningSecret(subscriptionId, now);
    }

    @Transactional
    public WebhookSigningSecretVersionView retireSigningSecret(
            StaffPrincipal principal,
            UUID subscriptionId,
            UUID versionId
    ) {
        var access = administrationAccessService.requireAdministratorForUpdate(principal);
        var subscription = requireSubscriptionForUpdate(subscriptionId, access.tenantId());
        requireIntegration(subscription.getIntegrationId(), access.tenantId(), true);
        var version = signingSecretRepository
                .findForUpdateByIdAndSubscriptionId(versionId, subscriptionId)
                .orElseThrow(() -> new IntegrationNotFoundException(
                        "Webhook signing-secret version was not found"
                ));
        if (version.isCurrent()) {
            throw new InvalidIntegrationRequestException(
                    "The current webhook signing secret cannot be retired"
            );
        }
        version.retire(clock.instant());
        return WebhookSigningSecretVersionView.from(version);
    }

    private IssuedWebhookSigningSecretView issueSigningSecret(UUID subscriptionId, java.time.Instant now) {
        var versionId = UUID.randomUUID();
        var issued = signingSecretCipher.issue(subscriptionId, versionId);
        var version = signingSecretRepository.save(WebhookSigningSecretVersion.issue(
                versionId,
                subscriptionId,
                issued.encryptedValue(),
                issued.nonce(),
                now
        ));
        return new IssuedWebhookSigningSecretView(
                WebhookSigningSecretVersionView.from(version),
                issued.value()
        );
    }

    private WebhookSubscriptionView toView(WebhookSubscription subscription) {
        var locationIds = locationAccessRepository.findAllBySubscriptionId(subscription.getId())
                .stream()
                .map(WebhookSubscriptionLocationAccess::getLocationId)
                .collect(Collectors.toUnmodifiableSet());
        var eventTypes = eventSelectionRepository.findAllBySubscriptionId(subscription.getId())
                .stream()
                .map(WebhookSubscriptionEventSelection::getEventType)
                .collect(Collectors.toUnmodifiableSet());
        var versions = signingSecretRepository
                .findAllBySubscriptionIdOrderByIssuedAtDesc(subscription.getId())
                .stream()
                .map(WebhookSigningSecretVersionView::from)
                .toList();
        return WebhookSubscriptionView.from(subscription, locationIds, eventTypes, versions);
    }

    private List<WebhookSubscriptionView> toViews(List<WebhookSubscription> subscriptions) {
        if (subscriptions.isEmpty()) {
            return List.of();
        }
        var subscriptionIds = subscriptions.stream()
                .map(WebhookSubscription::getId)
                .collect(Collectors.toUnmodifiableSet());
        var locationIdsBySubscription = locationAccessRepository
                .findAllBySubscriptionIdIn(subscriptionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        WebhookSubscriptionLocationAccess::getSubscriptionId,
                        Collectors.mapping(
                                WebhookSubscriptionLocationAccess::getLocationId,
                                Collectors.toUnmodifiableSet()
                        )
                ));
        var eventTypesBySubscription = eventSelectionRepository
                .findAllBySubscriptionIdIn(subscriptionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        WebhookSubscriptionEventSelection::getSubscriptionId,
                        Collectors.mapping(
                                WebhookSubscriptionEventSelection::getEventType,
                                Collectors.toUnmodifiableSet()
                        )
                ));
        var versionsBySubscription = signingSecretRepository
                .findAllBySubscriptionIdInOrderBySubscriptionIdAscIssuedAtDesc(subscriptionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        WebhookSigningSecretVersion::getSubscriptionId,
                        Collectors.mapping(
                                WebhookSigningSecretVersionView::from,
                                Collectors.toList()
                        )
                ));

        return subscriptions.stream()
                .map(subscription -> WebhookSubscriptionView.from(
                        subscription,
                        locationIdsBySubscription.getOrDefault(
                                subscription.getId(),
                                Collections.emptySet()
                        ),
                        eventTypesBySubscription.getOrDefault(
                                subscription.getId(),
                                Collections.emptySet()
                        ),
                        versionsBySubscription.getOrDefault(
                                subscription.getId(),
                                Collections.emptyList()
                        )
                ))
                .toList();
    }

    private void replaceSelections(
            WebhookSubscription subscription,
            Set<UUID> locationIds,
            Set<OrderEventType> eventTypes
    ) {
        locationAccessRepository.deleteAllBySubscriptionId(subscription.getId());
        eventSelectionRepository.deleteAllBySubscriptionId(subscription.getId());
        locationAccessRepository.flush();
        eventSelectionRepository.flush();
        locationAccessRepository.saveAll(locationIds.stream()
                .map(locationId -> WebhookSubscriptionLocationAccess.create(
                        subscription.getId(),
                        locationId,
                        subscription.getTenantId()
                ))
                .toList());
        eventSelectionRepository.saveAll(eventTypes.stream()
                .map(eventType -> WebhookSubscriptionEventSelection.create(
                        subscription.getId(),
                        eventType
                ))
                .toList());
    }

    private void validateSelections(
            UUID tenantId,
            Set<UUID> locationIds,
            Set<OrderEventType> eventTypes
    ) {
        if (locationIds == null || locationIds.isEmpty()) {
            throw new InvalidIntegrationRequestException(
                    "A webhook subscription requires at least one location"
            );
        }
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new InvalidIntegrationRequestException(
                    "A webhook subscription requires at least one event type"
            );
        }
        var locations = locationRepository.findAllById(locationIds);
        if (locations.size() != locationIds.size()
                || locations.stream().anyMatch(location -> !tenantId.equals(location.getTenantId()))) {
            throw new InvalidIntegrationRequestException(
                    "Every webhook location must belong to the administrator's tenant"
            );
        }
    }

    private void requireConfigured(UUID subscriptionId) {
        if (locationAccessRepository.findAllBySubscriptionId(subscriptionId).isEmpty()
                || eventSelectionRepository.findAllBySubscriptionId(subscriptionId).isEmpty()) {
            throw new InvalidIntegrationRequestException(
                    "A webhook subscription requires locations and event types before enabling"
            );
        }
    }

    private ExternalIntegration requireIntegration(
            UUID integrationId,
            UUID tenantId,
            boolean lockForUpdate
    ) {
        var integration = (lockForUpdate
                ? integrationRepository.findForUpdateByIdAndTenantId(integrationId, tenantId)
                : integrationRepository.findByIdAndTenantId(integrationId, tenantId))
                .orElseThrow(() -> new IntegrationNotFoundException(
                        "External Integration was not found"
                ));
        if (integration.isArchived()) {
            throw new IntegrationNotFoundException("External Integration was not found");
        }
        return integration;
    }

    private WebhookSubscription requireSubscriptionForUpdate(UUID subscriptionId, UUID tenantId) {
        var subscription = subscriptionRepository
                .findForUpdateByIdAndTenantId(subscriptionId, tenantId)
                .orElseThrow(() -> new IntegrationNotFoundException(
                        "Webhook subscription was not found"
                ));
        if (subscription.getStatus() == WebhookSubscriptionStatus.ARCHIVED) {
            throw new IntegrationNotFoundException("Webhook subscription was not found");
        }
        return subscription;
    }

    private static ManagedIntegrationName parseName(String candidate) {
        try {
            return ManagedIntegrationName.from(candidate);
        } catch (IllegalArgumentException exception) {
            throw new InvalidIntegrationRequestException(exception.getMessage(), exception);
        }
    }

    private String parseDestination(String candidate) {
        try {
            var destination = destinationPolicy.requireAllowed(candidate).toASCIIString();
            if (destination.length() > MAXIMUM_DESTINATION_LENGTH) {
                throw new InvalidIntegrationRequestException(
                        "Webhook destination cannot exceed 2048 ASCII characters"
                );
            }
            return destination;
        } catch (InvalidWebhookDestinationException exception) {
            throw new InvalidIntegrationRequestException(exception.getMessage(), exception);
        }
    }
}
