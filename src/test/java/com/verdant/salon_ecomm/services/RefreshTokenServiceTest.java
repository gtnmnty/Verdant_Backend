package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.exceptions.RefreshTokenExpiredException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.models.entities.RefreshToken;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import com.verdant.salon_ecomm.repositories.RefreshTokenRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.response.AuthResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// M-37: hashed storage, one token per session, rotation that revokes (not deletes), reuse detection.
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final long SEVEN_DAYS_MS = 604_800_000L;

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock UserRepository userRepository;
    @Mock JwtService jwtService;

    RefreshTokenService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepository, userRepository, jwtService);
        ReflectionTestUtils.setField(service, "refreshTokenDurationMs", SEVEN_DAYS_MS);
        user = User.builder().id(UUID.randomUUID()).email("jane@x.com")
            .status(AccountStatus.ACTIVE).emailVerified(true).build();
    }

    // Must match the SQL in V13: encode(sha256(convert_to(token, 'UTF8')), 'hex').
    static String sha256Hex(String raw) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private RefreshToken stored(String raw, boolean revoked, OffsetDateTime revokedAt, OffsetDateTime expiresAt)
            throws Exception {
        RefreshToken t = RefreshToken.builder().id(UUID.randomUUID()).user(user).tokenHash(sha256Hex(raw))
            .issuedAt(OffsetDateTime.now().minusDays(1)).expiresAt(expiresAt)
            .revoked(revoked).revokedAt(revokedAt).build();
        when(refreshTokenRepository.findByTokenHashForUpdate(sha256Hex(raw))).thenReturn(Optional.of(t));
        return t;
    }

    // ── create ───────────────────────────────────────────

    @Test
    void createStoresOnlyTheHashAndReturnsTheRawToken() throws Exception {
        when(userRepository.findByEmail("jane@x.com")).thenReturn(Optional.of(user));

        String raw = service.createRefreshToken("jane@x.com");

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(raw).matches("[A-Za-z0-9_-]{43}");          // 32 bytes, URL-safe Base64, no padding
        assertThat(saved.getValue().getTokenHash()).isEqualTo(sha256Hex(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getUser()).isSameAs(user);
        OffsetDateTime inSevenDays = OffsetDateTime.now().plusDays(7);
        assertThat(saved.getValue().getExpiresAt())
            .isBetween(inSevenDays.minusMinutes(1), inSevenDays.plusMinutes(1));
    }

    @Test
    void createKeepsOtherSessionsButCapsThemAtTen() {
        when(userRepository.findByEmail("jane@x.com")).thenReturn(Optional.of(user));
        List<RefreshToken> live = new ArrayList<>();
        for (int i = 0; i < 12; i++) live.add(RefreshToken.builder().id(UUID.randomUUID()).build());
        when(refreshTokenRepository.findByUser_IdAndRevokedFalseOrderByIssuedAtAsc(user.getId())).thenReturn(live);

        service.createRefreshToken("jane@x.com");

        // 12 live + 1 new would be 13: the 3 oldest go, leaving 10.
        verify(refreshTokenRepository).deleteAll(live.subList(0, 3));
        verify(refreshTokenRepository, never()).deleteByUserId(any());
        verify(refreshTokenRepository).deleteStaleForUser(eq(user.getId()), any(), any());
    }

    @Test
    void createForUnknownEmailFails() {
        when(userRepository.findByEmail("nobody@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRefreshToken("nobody@x.com"))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    // ── rotate ───────────────────────────────────────────

    @Test
    void rotateRevokesTheOldTokenAndIssuesANewOne() throws Exception {
        RefreshToken old = stored("old-raw", false, null, OffsetDateTime.now().plusDays(3));
        when(jwtService.generateToken(user)).thenReturn("access-jwt");
        when(jwtService.getExpirationTime()).thenReturn(3_600_000L);

        AuthResult result = service.rotateRefreshToken("old-raw");

        assertThat(old.isRevoked()).isTrue();
        assertThat(old.getRevokedAt()).isNotNull();
        assertThat(result.getToken()).isEqualTo("access-jwt");
        assertThat(result.getRefreshToken()).isNotBlank().isNotEqualTo("old-raw");
        assertThat(result.getExpiresInMs()).isEqualTo(3_600_000L);
        verify(refreshTokenRepository, never()).delete(old);
    }

    @Test
    void reusingARotatedTokenAfterTheGracePeriodEndsEverySession() throws Exception {
        stored("stolen", true, OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().plusDays(3));

        assertThatThrownBy(() -> service.rotateRefreshToken("stolen"))
            .isInstanceOf(RefreshTokenExpiredException.class);
        verify(refreshTokenRepository).deleteByUserId(user.getId());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void reuseWithinTheGracePeriodIsRefusedButNotPunished() throws Exception {
        // Two tabs refreshing at once: the second presents a token rotated a moment ago.
        stored("same-cookie", true, OffsetDateTime.now().minusSeconds(5), OffsetDateTime.now().plusDays(3));

        assertThatThrownBy(() -> service.rotateRefreshToken("same-cookie"))
            .isInstanceOf(RefreshTokenExpiredException.class);
        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    @Test
    void expiredTokenIsDeletedAndRefused() throws Exception {
        RefreshToken old = stored("expired", false, null, OffsetDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.rotateRefreshToken("expired"))
            .isInstanceOf(RefreshTokenExpiredException.class);
        verify(refreshTokenRepository).delete(old);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void suspendedAccountCannotRotate() throws Exception {
        user.setStatus(AccountStatus.SUSPENDED);
        RefreshToken old = stored("raw", false, null, OffsetDateTime.now().plusDays(3));

        assertThatThrownBy(() -> service.rotateRefreshToken("raw"))
            .isInstanceOf(RefreshTokenExpiredException.class);
        assertThat(old.isRevoked()).isFalse();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void unverifiedAccountCannotRotate() throws Exception {
        user.setEmailVerified(false);
        stored("raw", false, null, OffsetDateTime.now().plusDays(3));

        assertThatThrownBy(() -> service.rotateRefreshToken("raw"))
            .isInstanceOf(RefreshTokenExpiredException.class);
    }

    @Test
    void unknownOrBlankTokenIsNotFound() {
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotateRefreshToken("unknown")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.rotateRefreshToken(" ")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.rotateRefreshToken(null)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ── logout ───────────────────────────────────────────

    @Test
    void logoutEndsOnlyThisSession() throws Exception {
        RefreshToken mine = RefreshToken.builder().id(UUID.randomUUID()).user(user).build();
        when(refreshTokenRepository.findByTokenHash(sha256Hex("mine"))).thenReturn(Optional.of(mine));

        service.revokeSession("mine");

        verify(refreshTokenRepository).delete(mine);
        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    @Test
    void logoutWithUnknownOrMissingTokenDoesNothing() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        service.revokeSession("gone");
        service.revokeSession(null);
        service.revokeSession("");

        verify(refreshTokenRepository, never()).delete(any());
    }
}
