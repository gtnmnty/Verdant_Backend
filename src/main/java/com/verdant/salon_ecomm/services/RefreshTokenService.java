package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.entities.RefreshToken;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.exceptions.RefreshTokenExpiredException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.repositories.RefreshTokenRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${security.jwt.refresh-expiration-time}")
    private long refreshTokenDurationMs;

    /**
     * Creates a refresh token service with the given repositories.
     */
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a refresh token for the user identified by email.
     *
     * @param email the user's email address
     * @return the saved refresh token
     */
    @Transactional
    public RefreshToken createRefreshToken(String email) {
        User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new ResourceNotFoundException("Account not found."));

        refreshTokenRepository.deleteAllByUserId(user.getId());

        RefreshToken newrefreshToken = new RefreshToken();
        newrefreshToken.setUser(user);
        newrefreshToken.setToken(UUID.randomUUID().toString());
        newrefreshToken.setExpiresAt(OffsetDateTime.now().plusSeconds(refreshTokenDurationMs / 1000));

        return refreshTokenRepository.save(newrefreshToken);
    }

    /**
     * Validates that a refresh token has not expired.
     *
     * @param refreshToken the refresh token to validate
     * @return the same refresh token if it is still valid
     * @throws RefreshTokenExpiredException if the refresh token has expired
     */
    @Transactional
    public RefreshToken verifyRefreshToken(RefreshToken refreshToken) {
        if(refreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new RefreshTokenExpiredException("Refresh token was expired. Please sign in again.");
        }

        return refreshToken;
    }

//    @Transactional
//    public RefreshToken updateRefreshToken(RefreshToken refreshToken) {
//
/**
     * Deletes all refresh tokens for a user.
     *
     * @param userId the user's identifier
     */

    @Transactional
    public void deleteByUserId(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));

        refreshTokenRepository.deleteAllByUserId(user.getId());
    }

}
