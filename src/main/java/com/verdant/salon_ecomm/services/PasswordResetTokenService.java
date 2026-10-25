package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.user.events.UserPasswordChangedEvent;
import com.verdant.salon_ecomm.exceptions.ForbiddenException;
import com.verdant.salon_ecomm.models.entities.PasswordResetToken;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import com.verdant.salon_ecomm.repositories.PasswordResetTokenRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.validation.PasswordPolicy;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {

    // Public so the reset-link email can state the real expiry.
    // 1 hour for a reset on an existing account: short, since the user is expected to act right away.
    public static final Duration TOKEN_TTL = Duration.ofHours(1);
    // 24 hours for a brand-new staff account: the person may not read the invite until the next
    // morning. Tradeoff: a longer window in which a leaked invite email works; the link is still one-time.
    public static final Duration NEW_ACCOUNT_TOKEN_TTL = Duration.ofHours(24);
    private static final int TOKEN_BYTES = 32; // 256 bits of entropy

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();
    private final RefreshTokenService refreshTokenService;

    // Which lifetime applies to a link: new account invite vs a reset on an existing account.
    public static Duration ttlFor(boolean newAccount) {
        return newAccount ? NEW_ACCOUNT_TOKEN_TTL : TOKEN_TTL;
    }

    @Transactional
    public String issueToken(User user) {
        return issueToken(user, TOKEN_TTL);
    }

    @Transactional
    public String issueToken(User user, Duration ttl) {
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
            .expiresAt(now.plus(ttl))
            .createdAt(now)
            .build();

        tokenRepository.save(token);

        return rawToken;
    }

    // Validates the raw token from a reset link and, if valid, sets the new
    // password and burns the token (and any other outstanding ones for that
    // user) so it can't be replayed.
    @Transactional
    public void redeem(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ForbiddenException("Invalid or expired reset link.");
        }
        // Checked before the token is claimed, so a rejected password doesn't burn the link.
        if (!PasswordPolicy.isValid(newPassword)) {
            throw new ForbiddenException(PasswordPolicy.MESSAGE);
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

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // The token was only ever sent to user.getEmail(), so redeeming it proves the user
        // controls that mailbox. Without this, a staff-created account (emailVerified = false)
        // would set a password and still be rejected at login.
        // Safe after a staff email change: updateAccount burns every outstanding token, so a
        // link mailed to the old address can't verify the new one.
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(claimTime);
        }
        userRepository.save(user);
        refreshTokenService.deleteByUserId(user.getId());

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

    // Used when staff change an account's email: any reset link already mailed out
    // is marked used, so it can't be redeemed after the address moves.
    @Transactional
    public void invalidateAllFor(UUID userId) {
        tokenRepository.invalidateAllActiveTokensForUser(userId, OffsetDateTime.now());
    }
}
