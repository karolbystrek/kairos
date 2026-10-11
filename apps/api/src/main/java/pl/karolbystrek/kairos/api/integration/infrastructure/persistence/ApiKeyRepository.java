package pl.karolbystrek.kairos.api.integration.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.karolbystrek.kairos.api.integration.domain.ApiKey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    @EntityGraph(attributePaths = {"integration", "scopes", "locationAccess"})
    List<ApiKey> findAllByIntegration_IdAndTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            UUID integrationId,
            UUID tenantId
    );

    boolean existsByIntegration_IdAndNormalizedNameAndDeletedAtIsNull(UUID integrationId, String normalizedName);

    @Query("select apiKey.integration.id from ApiKey apiKey where apiKey.id = :apiKeyId and apiKey.tenantId = :tenantId")
    Optional<UUID> findIntegrationIdByIdAndTenantId(
        @Param("apiKeyId") UUID apiKeyId,
        @Param("tenantId") UUID tenantId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ApiKey> findForUpdateByIdAndTenantId(UUID apiKeyId, UUID tenantId);

    @EntityGraph(attributePaths = {"integration", "scopes", "locationAccess"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select distinct apiKey
        from ApiKey apiKey
        join apiKey.locationAccess locationAccess
        where apiKey.tenantId = :tenantId
          and locationAccess.id.locationId = :locationId
        """)
    List<ApiKey> findAllForUpdateByTenantIdAndLocationId(
        @Param("tenantId") UUID tenantId,
        @Param("locationId") UUID locationId
    );
}
