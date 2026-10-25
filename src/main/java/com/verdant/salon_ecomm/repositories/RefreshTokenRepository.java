package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    // M-37: lookups are by hash; the raw token is never stored.
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Row-locked, so two refreshes with the same cookie are handled one after another.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    // M-37: the user's live sessions, oldest first, for the per-user session cap.
    List<RefreshToken> findByUser_IdAndRevokedFalseOrderByIssuedAtAsc(UUID userId);

    // M-37: housekeeping on each new token: drops this user's expired tokens, and rotated (revoked) ones
    // past the window in which a reuse is still worth detecting.
    @Modifying
    @Query("""
        DELETE FROM RefreshToken r
        WHERE r.user.id = :userId
          AND (r.expiresAt < :now OR (r.revoked = true AND r.revokedAt < :revokedBefore))
        """)
    int deleteStaleForUser(@Param("userId") UUID userId, @Param("now") OffsetDateTime now,
                           @Param("revokedBefore") OffsetDateTime revokedBefore);

    // Signs a user out everywhere. Derived delete: loads the rows then removes them,
    // so it must run inside a transaction (all callers are @Transactional).
    // Bulk admin deletes don't need a variant: V2 migration cascades these rows at the DB level.
    @Modifying
    void deleteByUserId(UUID userId);
}
