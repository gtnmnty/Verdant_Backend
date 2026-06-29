package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.entities.RefreshToken;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.exceptions.RefreshTokenExpiredException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.repositories.RefreshTokenRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.response.AuthResult;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${security.jwt.refresh-expiration-time}")
    private long refreshTokenDurationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            JwtService jwtService
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public RefreshToken createRefreshToken(String email) {
        User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new ResourceNotFoundException("Account not found."));

        refreshTokenRepository.deleteAllByUserId(user.getId());

        RefreshToken newrefreshToken = new RefreshToken();
        newrefreshToken.setUser(user);
        newrefreshToken.setToken(UUID.randomUUID().toString());
        newrefreshToken.setJti(UUID.randomUUID().toString());
        newrefreshToken.setExpiresAt(OffsetDateTime.now().plusSeconds(refreshTokenDurationMs / 1000));

        return refreshTokenRepository.save(newrefreshToken);
    }

    @Transactional
    public RefreshToken verifyRefreshToken(RefreshToken refreshToken) {
        if(refreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new RefreshTokenExpiredException("Refresh token was expired. Please sign in again.");
        }

        return refreshToken;
    }

    @Transactional
    public RefreshToken findByToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Refresh token not found"));
    }

    @Transactional
    public AuthResult rotateRefreshToken(String incomingToken) {
        RefreshToken  oldRefreshToken = refreshTokenRepository.findByTokenForUpdate(incomingToken)
                .orElseThrow(() -> new ResourceNotFoundException("Refresh token not found"));

        if(oldRefreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            refreshTokenRepository.delete(oldRefreshToken);
            throw new RefreshTokenExpiredException("Refresh token was expired. Please sign in again.");
        }

        User user = oldRefreshToken.getUser();

        refreshTokenRepository.delete(oldRefreshToken);

        String newAccessToken = jwtService.generateToken(user);
        RefreshToken newRefreshToken = createRefreshToken(user.getEmail());
        long expirationTime = jwtService.getExpirationTime();

        return new AuthResult(
                newAccessToken,
                newRefreshToken.getToken(),
                expirationTime
        );
    }

    @Transactional
    public void deleteByUserId(UUID userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    public long getRefreshTokenDurationSeconds() {
        return refreshTokenDurationMs / 1000;
    }
}
