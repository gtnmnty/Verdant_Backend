package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.token = :token")
    Optional<RefreshToken> findByTokenForUpdate(@Param("token") String token);

    // Signs a user out everywhere. Derived delete: loads the rows then removes them,
    // so it must run inside a transaction (all callers are @Transactional).
    // Bulk admin deletes don't need a variant: V2 migration cascades these rows at the DB level.
    @Modifying
    void deleteByUserId(UUID userId);
}
