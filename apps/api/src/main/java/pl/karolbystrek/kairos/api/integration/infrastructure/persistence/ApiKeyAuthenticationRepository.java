package pl.karolbystrek.kairos.api.integration.infrastructure.persistence;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import pl.karolbystrek.kairos.api.integration.domain.ApiKey;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyAuthenticationRepository extends Repository<ApiKey, UUID> {

    @Query(value = """
        SELECT secret_hash AS "secretHash", tenant_id AS "tenantId", integration_id AS "integrationId",
            api_key_id AS "apiKeyId", (last_used_at IS NULL OR last_used_at <= :usageStaleAt) AS "usageRecordDue", scopes,
            location_ids AS "locationIds"
        FROM public.api_key_authentication(:apiKeyId, :now)
        """, nativeQuery = true)
    Optional<Credentials> findCredentials(UUID apiKeyId, Instant now, Instant usageStaleAt);

    @Query(value = "SELECT public.record_api_key_use(:apiKeyId, :now, :usageStaleAt)", nativeQuery = true)
    Boolean recordUse(UUID apiKeyId, Instant now, Instant usageStaleAt);

    interface Credentials {
        String getSecretHash();
        UUID getTenantId();
        UUID getIntegrationId();
        UUID getApiKeyId();
        Boolean getUsageRecordDue();
        String[] getScopes();
        UUID[] getLocationIds();
    }
}
