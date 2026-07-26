package pl.karolbystrek.kairos.api.integration.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.integration.domain.ApiKey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    @EntityGraph(attributePaths = {"integration", "scopes", "locationAccess"})
    List<ApiKey> findAllByIntegration_IdAndTenantIdOrderByCreatedAtAsc(
            UUID integrationId,
            UUID tenantId
    );

    boolean existsByIntegration_IdAndNormalizedName(UUID integrationId, String normalizedName);

    Optional<ApiKey> findByIdAndTenantId(UUID apiKeyId, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ApiKey> findForUpdateByIdAndTenantId(UUID apiKeyId, UUID tenantId);
}
