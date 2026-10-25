package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.models.entities.RefreshToken;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.exceptions.RefreshTokenExpiredException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.repositories.RefreshTokenRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.response.AuthResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

// M-37 overview:
//   - Only a SHA-256 hash of each refresh token is stored (same scheme as PasswordResetTokenService);
//     the raw value exists only in the cookie. A database leak no longer hands out live sessions.
//   - One token per device/session, up to MAX_SESSIONS_PER_USER. Logging in no longer deletes the user's
//     other tokens, so signing in on the phone doesn't sign out the browser.
//   - Rotation marks the old token revoked instead of deleting it. If that revoked token is presented again
//     (after a short grace period), it was copied, and every session of the user is ended (reuse detection).
@Slf4j
@Service
public class RefreshTokenService {

    // Oldest sessions beyond this are dropped when a new one starts, so the table can't grow without limit.
    private static final int MAX_SESSIONS_PER_USER = 10;
    // Two tabs refreshing with the same cookie at once: the second sees a token revoked a moment ago. That
    // is not theft, so it is only refused (the browser already holds the new cookie), not punished.
    private static final Duration REUSE_GRACE = Duration.ofSeconds(30);
    // How long a rotated token is kept so a later reuse can still be recognized.
    private static final Duration REVOKED_RETENTION = Duration.ofDays(1);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

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

    // Starts a new session for this user and returns the RAW token for the cookie. It is the only time the
    // raw value is available; only its hash is saved.
    @Transactional
    public String createRefreshToken(String email) {
        User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new ResourceNotFoundException("Account not found."));
        return issueFor(user);
    }

    // M-37 flow: lock the presented token's row -> revoked? (theft unless within the grace period: end every
    // session) -> expired? (delete it) -> account still usable? -> mark it revoked -> issue a new token.
    // noRollbackFor: the deletes done before throwing RefreshTokenExpiredException must commit. Before, the
    // expired-token delete was rolled back together with the exception.
    @Transactional(noRollbackFor = RefreshTokenExpiredException.class)
    public AuthResult rotateRefreshToken(String incomingToken) {
        if (incomingToken == null || incomingToken.isBlank()) {
            throw new ResourceNotFoundException("Refresh token not found");
        }
        RefreshToken oldRefreshToken = refreshTokenRepository.findByTokenHashForUpdate(hash(incomingToken))
                .orElseThrow(() -> new ResourceNotFoundException("Refresh token not found"));
        OffsetDateTime now = OffsetDateTime.now();
        User user = oldRefreshToken.getUser();

        if (oldRefreshToken.isRevoked()) {
            boolean withinGrace = oldRefreshToken.getRevokedAt() != null
                && oldRefreshToken.getRevokedAt().isAfter(now.minus(REUSE_GRACE));
            if (!withinGrace) {
                log.warn("Rotated refresh token reused for user {}; ending all of their sessions", user.getId());
                refreshTokenRepository.deleteByUserId(user.getId());
            }
            throw new RefreshTokenExpiredException("Please sign in again.");
        }

        if (oldRefreshToken.getExpiresAt().isBefore(now)) {
            refreshTokenRepository.delete(oldRefreshToken);
            throw new RefreshTokenExpiredException("Refresh token was expired. Please sign in again.");
        }

        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            throw new RefreshTokenExpiredException("Please sign in again.");
        }

        oldRefreshToken.setRevoked(true);
        oldRefreshToken.setRevokedAt(now);

        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = issueFor(user);
        long expirationTime = jwtService.getExpirationTime();

        return new AuthResult(
                newAccessToken,
                newRefreshToken,
                expirationTime
        );
    }

    // M-37: logout ends only THIS session (it used to delete every token of the user). Unknown or already
    // removed tokens are ignored, so logging out never fails.
    @Transactional
    public void revokeSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(refreshTokenRepository::delete);
    }

    // Signs the user out on every device (password change/reset, account suspended or deleted).
    @Transactional
    public void deleteByUserId(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    public long getRefreshTokenDurationSeconds() {
        return refreshTokenDurationMs / 1000;
    }

    // ── Helpers ──────────────────────────────────────────

    // Housekeeping (expired and long-revoked tokens, session cap), then a new token: 32 random bytes,
    // URL-safe Base64 for the cookie, SHA-256 hex in the database.
    private String issueFor(User user) {
        OffsetDateTime now = OffsetDateTime.now();
        refreshTokenRepository.deleteStaleForUser(user.getId(), now, now.minus(REVOKED_RETENTION));

        List<RefreshToken> live = refreshTokenRepository.findByUser_IdAndRevokedFalseOrderByIssuedAtAsc(user.getId());
        int excess = live.size() - (MAX_SESSIONS_PER_USER - 1);
        if (excess > 0) {
            refreshTokenRepository.deleteAll(live.subList(0, excess));
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        refreshTokenRepository.save(RefreshToken.builder()
            .user(user)
            .tokenHash(hash(rawToken))
            .expiresAt(now.plusSeconds(refreshTokenDurationMs / 1000))
            .build());
        return rawToken;
    }

    // Must stay identical to the SQL in V13 (hex SHA-256 of the UTF-8 bytes), or migrated sessions break.
    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed on every standard JVM.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
