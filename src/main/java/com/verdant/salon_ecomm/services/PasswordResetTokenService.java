package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.user.events.UserPasswordChangedEvent;
import com.verdant.salon_ecomm.entities.PasswordResetToken;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.exceptions.ForbiddenException;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import com.verdant.salon_ecomm.repositories.PasswordResetTokenRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {

    private static final Duration TOKEN_TTL = Duration.ofHours(1);
    private static final int TOKEN_BYTES = 32; // 256 bits of entropy

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String issueToken(User user) {
        OffsetDateTime now = OffsetDateTime.now();

        // Close out any older still-active tokens first — only one live reset
        // link per user at a time avoids confusion and shrinks the attack window.
        tokenRepository.invalidateAllActiveTokensForUser(user.getId(), now);

        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        PasswordResetToken token = PasswordResetToken.builder()
            .user(user)
            .tokenHash(hash(rawToken))
            .expiresAt(now.plus(TOKEN_TTL))
            .createdAt(now)
            .build();

        tokenRepository.save(token);

        return rawToken;
    }

     // Validates the raw token from a reset link and, if valid, sets the new
     //password and burns the token (and any other outstanding ones for that
     // user) so it can't be replayed.
    @Transactional
    public void redeem(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ForbiddenException("Invalid or expired reset link.");
        }

        String tokenHash = hash(rawToken);
               OffsetDateTime claimTime = OffsetDateTime.now();

        // Atomically claim the token: only one concurrent caller can flip
        // used_at from NULL to a value, closing the check-then-act race.
        int claimed = tokenRepository.markUsedIfActive(tokenHash, claimTime);
        if (claimed == 0) {
            throw new ForbiddenException("Invalid or expired reset link.");
        }

        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new ForbiddenException("Invalid or expired reset link."));

        User user = userRepository.findByIdForUpdate(token.getUser().getId())
            .orElseThrow(() -> new ForbiddenException("Invalid or expired reset link."));

        if (AccountStatus.BANNED.equals(user.getStatus()) || AccountStatus.DELETED.equals(user.getStatus())) {
            throw new ForbiddenException("Account is " + user.getStatus().toString().toLowerCase());
        }

        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 8) {
            throw new ForbiddenException("Password does not meet the minimum requirements.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepository.invalidateAllActiveTokensForUser(user.getId(), claimTime);

        // Reuses the same audit + security-notification path as a self-service
        // password change, since from the account's perspective it's the same
        // event: the password changed.
        eventPublisher.publishEvent(new UserPasswordChangedEvent(user));
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every standard JVM; this is
            // unreachable in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
