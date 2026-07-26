package pl.karolbystrek.kairos.api.integration.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pl.karolbystrek.kairos.api.integration.domain.ApiKeyVersion;

import java.util.List;
import java.util.UUID;

public interface ApiKeyVersionRepository extends JpaRepository<ApiKeyVersion, UUID> {

    List<ApiKeyVersion> findAllByApiKey_IdOrderByIssuedAtDesc(UUID apiKeyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ApiKeyVersion> findAllForUpdateByApiKey_IdOrderByIssuedAtDesc(UUID apiKeyId);
}
