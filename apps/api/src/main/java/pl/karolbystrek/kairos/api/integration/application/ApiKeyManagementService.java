package pl.karolbystrek.kairos.api.integration.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationConflictException;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationNotFoundException;
import pl.karolbystrek.kairos.api.integration.application.exception.InvalidIntegrationRequestException;
import pl.karolbystrek.kairos.api.integration.application.model.ApiKeyView;
import pl.karolbystrek.kairos.api.integration.application.model.IssuedApiKeyView;
import pl.karolbystrek.kairos.api.integration.domain.ApiKey;
import pl.karolbystrek.kairos.api.integration.domain.ApiKeyScope;
import pl.karolbystrek.kairos.api.integration.domain.ExternalIntegration;
import pl.karolbystrek.kairos.api.integration.domain.ManagedIntegrationName;
import pl.karolbystrek.kairos.api.integration.infrastructure.persistence.ApiKeyRepository;
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

    private static final String ADMINISTRATOR_REQUIRED = "External Integration management requires a tenant administrator";

    private final ApiKeyRepository apiKeyRepository;
    private final ExternalIntegrationRepository integrationRepository;
    private final LocationRepository locationRepository;
    private final StaffAccessService staffAccessService;
    private final ApiKeyCredentialService credentialService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ApiKeyView> list(StaffPrincipal principal, UUID integrationId) {
        var access = staffAccessService.resolve(principal).requireTenantAdmin(ADMINISTRATOR_REQUIRED);
        requireManageableIntegration(integrationId, access.tenantId());
        return apiKeyRepository.findAllByIntegration_IdAndTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(
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
        var access = staffAccessService.resolveForUpdate(principal).requireTenantAdmin(ADMINISTRATOR_REQUIRED);
        var integration = requireManageableIntegrationForUpdate(integrationId, access.tenantId());
        var name = parseName(candidateName);
        var scopes = parseScopes(requestedScopes);
        var locations = validateLocations(locationIds, access.tenantId());
        var now = clock.instant();

        if (apiKeyRepository.existsByIntegration_IdAndNormalizedNameAndDeletedAtIsNull(
                integrationId,
                name.normalizedValue()
        )) {
            throw new IntegrationConflictException("An API Key with this name already exists");
        }

        var credential = credentialService.issue();
        final ApiKey apiKey;
        try {
            apiKey = ApiKey.issue(
                    credential.apiKeyId(),
                    integration,
                    name,
                    scopes,
                    locations,
                    credential.hash(),
                    expiresAt,
                    now
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new InvalidIntegrationRequestException(exception.getMessage(), exception);
        }

        try {
            var savedApiKey = apiKeyRepository.saveAndFlush(apiKey);
            return new IssuedApiKeyView(ApiKeyView.from(savedApiKey), credential.value());
        } catch (DataIntegrityViolationException exception) {
            throw new IntegrationConflictException("The API Key could not be issued", exception);
        }
    }

    @Transactional
    public void delete(StaffPrincipal principal, UUID apiKeyId) {
        var access = staffAccessService.resolveForUpdate(principal).requireTenantAdmin(ADMINISTRATOR_REQUIRED);
        var apiKey = requireManageableKeyForUpdate(apiKeyId, access.tenantId());
        apiKey.delete(clock.instant());
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

    private ApiKey requireManageableKeyForUpdate(UUID apiKeyId, UUID tenantId) {
        if (apiKeyId == null) {
            throw new InvalidIntegrationRequestException("API Key ID is required");
        }
        // Resolve the owner without loading the key, so the locked read below sees concurrent deletes.
        var integrationId = apiKeyRepository.findIntegrationIdByIdAndTenantId(apiKeyId, tenantId)
                .orElseThrow(() -> new IntegrationNotFoundException("API Key was not found"));
        var integration = integrationRepository.findForUpdateByIdAndTenantId(integrationId, tenantId)
                .orElseThrow(() -> new IntegrationNotFoundException("API Key was not found"));
        if (integration.isArchived()) {
            throw new IntegrationNotFoundException("API Key was not found");
        }
        var apiKey = apiKeyRepository.findForUpdateByIdAndTenantId(apiKeyId, tenantId)
                .orElseThrow(() -> new IntegrationNotFoundException("API Key was not found"));
        if (apiKey.isDeleted()) {
            throw new IntegrationNotFoundException("API Key was not found");
        }
        return apiKey;
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
                || locations.stream().anyMatch(location ->
                    !location.getTenantId().equals(tenantId) || !location.isEnabled()
                )) {
            throw new InvalidIntegrationRequestException(
                    "Every API Key location must be enabled in the current tenant"
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
