package com.verdant.salon_ecomm.models.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

// One signed-in session (device). M-37: a user can have several at once (web and mobile); logging in on
// one device no longer signs the others out.
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // M-37: hex SHA-256 of the token in the cookie. The raw token is only ever in the cookie, so a database
    // leak no longer hands out live sessions. Replaces the plain "token" column and the unused "jti" (V13).
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    // M-37: set when the token is rotated. The row is kept for a while so a second use can be recognized
    // as theft (see RefreshTokenService.rotateRefreshToken).
    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @PrePersist
    protected void onCreate() {
        if (this.issuedAt == null) {
            this.issuedAt = OffsetDateTime.now();
        }
    }
}
