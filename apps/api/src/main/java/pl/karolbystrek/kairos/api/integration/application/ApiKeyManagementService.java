package pl.karolbystrek.kairos.api.integration.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationConflictException;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationNotFoundException;
import pl.karolbystrek.kairos.api.integration.application.exception.InvalidIntegrationRequestException;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyVersionView;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyView;
import pl.karolbystrek.kairos.api.integration.application.model.IssuedApiKeyVersionView;
import pl.karolbystrek.kairos.api.integration.application.model.IssuedApiKeyView;
import pl.karolbystrek.kairos.api.integration.domain.ApiKey;
import pl.karolbystrek.kairos.api.integration.domain.ApiKeyScope;
import pl.karolbystrek.kairos.api.integration.domain.ApiKeyVersion;
import pl.karolbystrek.kairos.api.integration.domain.ExternalIntegration;
import pl.karolbystrek.kairos.api.integration.domain.ManagedIntegrationName;
import pl.karolbystrek.kairos.api.integration.infrastructure.config.ExternalIntegrationProperties;
import pl.karolbystrek.kairos.api.integration.infrastructure.persistence.ApiKeyRepository;
import pl.karolbystrek.kairos.api.integration.infrastructure.persistence.ApiKeyVersionRepository;
import pl.karolbystrek.kairos.api.integration.infrastructure.persistence.ExternalIntegrationRepository;
import pl.karolbystrek.kairos.api.location.infrastructure.persistence.LocationRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyManagementService {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyVersionRepository versionRepository;
    private final ExternalIntegrationRepository integrationRepository;
    private final LocationRepository locationRepository;
    private final IntegrationAdministrationAccessService administrationAccessService;
    private final ApiKeyCredentialService credentialService;
    private final ExternalIntegrationProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ApiKeyView> list(StaffPrincipal principal, UUID integrationId) {
        var access = administrationAccessService.requireAdministrator(principal);
        requireManageableIntegration(integrationId, access.tenantId());
        return apiKeyRepository.findAllByIntegration_IdAndTenantIdOrderByCreatedAtAsc(
                        integrationId,
                        access.tenantId()
                ).stream()
                .map(ApiKeyView::from)
                .toList();
    }

    @Transactional
    public IssuedApiKeyView issue(
            StaffPrincipal principal,
            UUID integrationId,
            String candidateName,
            Set<String> requestedScopes,
            Set<UUID> locationIds,
            Instant expiresAt
    ) {
        var access = administrationAccessService.requireAdministratorForUpdate(principal);
        var integration = requireManageableIntegrationForUpdate(integrationId, access.tenantId());
        var name = parseName(candidateName);
        var scopes = parseScopes(requestedScopes);
        var locations = validateLocations(locationIds, access.tenantId());
        var now = clock.instant();

        if (apiKeyRepository.existsByIntegration_IdAndNormalizedName(
                integrationId,
                name.normalizedValue()
        )) {
            throw new IntegrationConflictException("An API Key with this name already exists");
        }

        final ApiKey apiKey;
        try {
            apiKey = ApiKey.issue(integration, name, scopes, locations, expiresAt, now);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new InvalidIntegrationRequestException(exception.getMessage(), exception);
        }
        var credential = credentialService.issue();
        var version = ApiKeyVersion.issue(credential.versionId(), apiKey, credential.hash(), now);

        try {
            var savedApiKey = apiKeyRepository.saveAndFlush(apiKey);
            var savedVersion = versionRepository.saveAndFlush(version);
            return new IssuedApiKeyView(
                    ApiKeyView.from(savedApiKey),
                    ApiKeyVersionView.from(savedVersion),
                    credential.value()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new IntegrationConflictException("The API Key could not be issued", exception);
        }
    }

    @Transactional
    public ApiKeyView revoke(StaffPrincipal principal, UUID apiKeyId) {
        var access = administrationAccessService.requireAdministratorForUpdate(principal);
        var apiKey = requireManageableKeyForUpdate(apiKeyId, access.tenantId());
        apiKey.revoke(clock.instant());
        return ApiKeyView.from(apiKey);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyVersionView> listVersions(StaffPrincipal principal, UUID apiKeyId) {
        var access = administrationAccessService.requireAdministrator(principal);
        requireManageableKey(apiKeyId, access.tenantId());
        return versionRepository.findAllByApiKey_IdOrderByIssuedAtDesc(apiKeyId).stream()
                .map(ApiKeyVersionView::from)
                .toList();
    }

    @Transactional
    public IssuedApiKeyVersionView rotate(StaffPrincipal principal, UUID apiKeyId) {
        var access = administrationAccessService.requireAdministratorForUpdate(principal);
        var apiKey = requireManageableKeyForUpdate(apiKeyId, access.tenantId());
        var now = clock.instant();
        if (!apiKey.canIssueVersionAt(now)) {
            throw new IntegrationConflictException("The API Key cannot issue another version");
        }

        var versions = versionRepository.findAllForUpdateByApiKey_IdOrderByIssuedAtDesc(apiKeyId);
        var current = versions.stream()
                .filter(ApiKeyVersion::isCurrent)
                .findFirst()
                .orElseThrow(() -> new IntegrationConflictException(
                        "The API Key does not have a current version"
                ));
        versions.stream()
                .filter(version -> version != current && version.getRetiredAt() == null)
                .forEach(version -> version.retire(now));
        current.beginGracePeriod(now.plus(properties.apiKeyRotationGrace()));

        try {
            var credential = credentialService.issue();
            var replacement = ApiKeyVersion.issue(
                    credential.versionId(),
                    apiKey,
                    credential.hash(),
                    now
            );
            var savedVersion = versionRepository.saveAndFlush(replacement);
            return new IssuedApiKeyVersionView(
                    ApiKeyVersionView.from(savedVersion),
                    credential.value()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new IntegrationConflictException("The API Key version could not be rotated", exception);
        }
    }

    private ExternalIntegration requireManageableIntegration(UUID integrationId, UUID tenantId) {
        if (integrationId == null) {
            throw new InvalidIntegrationRequestException("External Integration ID is required");
        }
        var integration = integrationRepository.findByIdAndTenantId(integrationId, tenantId)
                .orElseThrow(() -> new IntegrationNotFoundException(
                        "External Integration was not found"
                ));
        if (integration.isArchived()) {
            throw new IntegrationNotFoundException("External Integration was not found");
        }
        return integration;
    }

    private ExternalIntegration requireManageableIntegrationForUpdate(UUID integrationId, UUID tenantId) {
        if (integrationId == null) {
            throw new InvalidIntegrationRequestException("External Integration ID is required");
        }
        var integration = integrationRepository.findForUpdateByIdAndTenantId(integrationId, tenantId)
                .orElseThrow(() -> new IntegrationNotFoundException(
                        "External Integration was not found"
                ));
        if (integration.isArchived()) {
            throw new IntegrationNotFoundException("External Integration was not found");
        }
        return integration;
    }

    private ApiKey requireManageableKey(UUID apiKeyId, UUID tenantId) {
        if (apiKeyId == null) {
            throw new InvalidIntegrationRequestException("API Key ID is required");
        }
        var apiKey = apiKeyRepository.findByIdAndTenantId(apiKeyId, tenantId)
                .orElseThrow(() -> new IntegrationNotFoundException("API Key was not found"));
        if (apiKey.getIntegration().isArchived()) {
            throw new IntegrationNotFoundException("API Key was not found");
        }
        return apiKey;
    }

    private ApiKey requireManageableKeyForUpdate(UUID apiKeyId, UUID tenantId) {
        if (apiKeyId == null) {
            throw new InvalidIntegrationRequestException("API Key ID is required");
        }
        var reference = apiKeyRepository.findByIdAndTenantId(apiKeyId, tenantId)
                .orElseThrow(() -> new IntegrationNotFoundException("API Key was not found"));
        var integration = integrationRepository.findForUpdateByIdAndTenantId(
                        reference.getIntegrationId(),
                        tenantId
                )
                .orElseThrow(() -> new IntegrationNotFoundException("API Key was not found"));
        if (integration.isArchived()) {
            throw new IntegrationNotFoundException("API Key was not found");
        }
        return apiKeyRepository.findForUpdateByIdAndTenantId(apiKeyId, tenantId)
                .orElseThrow(() -> new IntegrationNotFoundException("API Key was not found"));
    }

    private Set<UUID> validateLocations(Set<UUID> locationIds, UUID tenantId) {
        if (locationIds == null
                || locationIds.isEmpty()
                || locationIds.stream().anyMatch(locationId -> locationId == null)) {
            throw new InvalidIntegrationRequestException("At least one API Key location is required");
        }
        var requestedIds = Set.copyOf(locationIds);
        var locations = locationRepository.findAllById(requestedIds);
        if (locations.size() != requestedIds.size()
                || locations.stream().anyMatch(location -> !location.getTenantId().equals(tenantId))) {
            throw new InvalidIntegrationRequestException(
                    "Every API Key location must belong to the current tenant"
            );
        }
        return requestedIds;
    }

    private static ManagedIntegrationName parseName(String candidate) {
        try {
            return ManagedIntegrationName.from(candidate);
        } catch (IllegalArgumentException exception) {
            throw new InvalidIntegrationRequestException(exception.getMessage(), exception);
        }
    }

    private static Set<ApiKeyScope> parseScopes(Set<String> requestedScopes) {
        if (requestedScopes == null || requestedScopes.isEmpty()) {
            throw new InvalidIntegrationRequestException("At least one API Key scope is required");
        }

        try {
            var scopes = new HashSet<ApiKeyScope>();
            for (var requestedScope : requestedScopes) {
                scopes.add(ApiKeyScope.fromExternalValue(requestedScope));
            }
            return ApiKeyScope.normalize(scopes);
        } catch (IllegalArgumentException exception) {
            throw new InvalidIntegrationRequestException(exception.getMessage(), exception);
        }
    }
}
