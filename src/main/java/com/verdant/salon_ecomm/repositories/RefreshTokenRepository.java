package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByJti(String jti);
    void deleteAllByUserId(UUID userId);
}