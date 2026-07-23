package pl.karolbystrek.kairos.api.authentication.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.karolbystrek.kairos.api.authentication.domain.RefreshSession;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    Optional<RefreshSessionReference> findReferenceByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshSession> findForUpdateById(UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshSession> findForUpdateByRefreshTokenHash(String refreshTokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update RefreshSession session
        set session.revokedAt = :revokedAt
        where session.tokenFamilyId = :tokenFamilyId
          and session.revokedAt is null
        """)
    int revokeFamily(
        @Param("tokenFamilyId") UUID tokenFamilyId,
        @Param("revokedAt") Instant revokedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update RefreshSession session
        set session.revokedAt = :revokedAt
        where session.accountId = :accountId
          and session.revokedAt is null
        """)
    int revokeAllForAccount(
        @Param("accountId") UUID accountId,
        @Param("revokedAt") Instant revokedAt
    );

    interface RefreshSessionReference {

        UUID getId();

        UUID getAccountId();

        UUID getTokenFamilyId();
    }
}
