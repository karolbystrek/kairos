package pl.karolbystrek.kairos.api.integration.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationConflictException;
import pl.karolbystrek.kairos.api.integration.application.exception.IntegrationNotFoundException;
import pl.karolbystrek.kairos.api.integration.application.exception.InvalidIntegrationRequestException;
import pl.karolbystrek.kairos.api.integration.application.model.ExternalIntegrationView;
import pl.karolbystrek.kairos.api.integration.domain.ExternalIntegration;
import pl.karolbystrek.kairos.api.integration.domain.ExternalIntegrationStatus;
import pl.karolbystrek.kairos.api.integration.domain.ManagedIntegrationName;
import pl.karolbystrek.kairos.api.integration.infrastructure.persistence.ExternalIntegrationRepository;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExternalIntegrationManagementService {

    private final ExternalIntegrationRepository integrationRepository;
    private final IntegrationAdministrationAccessService administrationAccessService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ExternalIntegrationView> list(StaffPrincipal principal) {
        var access = administrationAccessService.requireAdministrator(principal);
        return integrationRepository.findAllByTenantIdAndStatusNotOrderByCreatedAtAsc(
                        access.tenantId(),
                        ExternalIntegrationStatus.ARCHIVED
                ).stream()
                .map(ExternalIntegrationView::from)
                .toList();
    }

    @Transactional
    public ExternalIntegrationView create(StaffPrincipal principal, String candidateName) {
        var access = administrationAccessService.requireAdministratorForUpdate(principal);
        var name = parseName(candidateName);
        if (integrationRepository.existsByTenantIdAndNormalizedName(
                access.tenantId(),
                name.normalizedValue()
        )) {
            throw new IntegrationConflictException("An External Integration with this name already exists");
        }

        var integration = ExternalIntegration.create(access.tenantId(), name, clock.instant());
        return save(integration);
    }

    @Transactional
    public ExternalIntegrationView rename(
            StaffPrincipal principal,
            UUID integrationId,
            String candidateName
    ) {
        var access = administrationAccessService.requireAdministratorForUpdate(principal);
        var integration = requireManageableForUpdate(integrationId, access.tenantId());
        var name = parseName(candidateName);
        if (!integration.getNormalizedName().equals(name.normalizedValue())
                && integrationRepository.existsByTenantIdAndNormalizedName(
                        access.tenantId(),
                        name.normalizedValue()
                )) {
            throw new IntegrationConflictException("An External Integration with this name already exists");
        }

        try {
            integration.rename(name, clock.instant());
        } catch (IllegalStateException exception) {
            throw new InvalidIntegrationRequestException(exception.getMessage(), exception);
        }
        return save(integration);
    }

    @Transactional
    public ExternalIntegrationView changeStatus(
            StaffPrincipal principal,
            UUID integrationId,
            ExternalIntegrationStatus target
    ) {
        if (target == null) {
            throw new InvalidIntegrationRequestException("External Integration status is required");
        }

        var access = administrationAccessService.requireAdministratorForUpdate(principal);
        var integration = requireManageableForUpdate(integrationId, access.tenantId());
        try {
            integration.changeStatus(target, clock.instant());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new InvalidIntegrationRequestException(exception.getMessage(), exception);
        }
        return ExternalIntegrationView.from(integration);
    }

    @Transactional
    public void archive(StaffPrincipal principal, UUID integrationId) {
        var access = administrationAccessService.requireAdministratorForUpdate(principal);
        var integration = requireForUpdate(integrationId, access.tenantId());
        integration.archive(clock.instant());
    }

    private ExternalIntegration requireForUpdate(UUID integrationId, UUID tenantId) {
        return integrationRepository.findForUpdateByIdAndTenantId(integrationId, tenantId)
                .orElseThrow(() -> new IntegrationNotFoundException("External Integration was not found"));
    }

    private ExternalIntegration requireManageableForUpdate(UUID integrationId, UUID tenantId) {
        var integration = requireForUpdate(integrationId, tenantId);
        if (integration.isArchived()) {
            throw new IntegrationNotFoundException("External Integration was not found");
        }
        return integration;
    }

    private ExternalIntegrationView save(ExternalIntegration integration) {
        try {
            var savedIntegration = integrationRepository.saveAndFlush(integration);
            return ExternalIntegrationView.from(savedIntegration);
        } catch (DataIntegrityViolationException exception) {
            throw new IntegrationConflictException(
                    "An External Integration with this name already exists",
                    exception
            );
        }
    }

    private static ManagedIntegrationName parseName(String candidate) {
        try {
            return ManagedIntegrationName.from(candidate);
        } catch (IllegalArgumentException exception) {
            throw new InvalidIntegrationRequestException(exception.getMessage(), exception);
        }
    }
}
