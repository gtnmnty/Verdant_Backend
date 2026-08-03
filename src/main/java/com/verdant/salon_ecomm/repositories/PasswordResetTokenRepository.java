package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // Invalidates every other outstanding token for a user once one is redeemed
    // (or a new one is issued) — closes the window where an older, still-valid
    // reset link could be used after the account holder already reset via a
    // newer one.
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.user.id = :userId AND t.usedAt IS NULL")
    void invalidateAllActiveTokensForUser(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    // Housekeeping hook for a scheduled cleanup job, if one gets added later.
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
