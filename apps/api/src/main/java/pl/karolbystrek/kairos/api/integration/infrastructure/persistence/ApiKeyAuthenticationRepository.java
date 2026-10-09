package pl.karolbystrek.kairos.api.integration.infrastructure.persistence;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import pl.karolbystrek.kairos.api.integration.domain.ApiKeyVersion;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyAuthenticationRepository extends Repository<ApiKeyVersion, UUID> {

    @Query(value = """
        SELECT secret_hash AS "secretHash", tenant_id AS "tenantId", integration_id AS "integrationId",
            api_key_id AS "apiKeyId", api_key_version_id AS "apiKeyVersionId", scopes,
            location_ids AS "locationIds"
        FROM public.api_key_authentication(:versionId, :now)
        """, nativeQuery = true)
    Optional<Credentials> findCredentials(UUID versionId, Instant now);

    interface Credentials {
        String getSecretHash();
        UUID getTenantId();
        UUID getIntegrationId();
        UUID getApiKeyId();
        UUID getApiKeyVersionId();
        String[] getScopes();
        UUID[] getLocationIds();
    }
}
