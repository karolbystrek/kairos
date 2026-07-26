package pl.karolbystrek.kairos.api.integration.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.integration.domain.ExternalIntegration;
import pl.karolbystrek.kairos.api.integration.domain.ExternalIntegrationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalIntegrationRepository extends JpaRepository<ExternalIntegration, UUID> {

    List<ExternalIntegration> findAllByTenantIdAndStatusNotOrderByCreatedAtAsc(
            UUID tenantId,
            ExternalIntegrationStatus status
    );

    Optional<ExternalIntegration> findByIdAndTenantId(UUID integrationId, UUID tenantId);

    boolean existsByTenantIdAndNormalizedName(UUID tenantId, String normalizedName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ExternalIntegration> findForUpdateByIdAndTenantId(UUID integrationId, UUID tenantId);
}
