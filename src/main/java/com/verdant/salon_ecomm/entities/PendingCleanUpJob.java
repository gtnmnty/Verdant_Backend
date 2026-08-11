package com.verdant.salon_ecomm.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
    name = "pending_cleanup_jobs",
    indexes = {
        @Index(name = "idx_cleanup_jobs_unprocessed", columnList = "processed")
    }
)
public class PendingCleanUpJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "avatar_public_id")
    private String avatarPublicId;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(nullable = false)
    private boolean processed = false;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "claimed_by")
    private String claimedBy;

    @Column(name = "claim_expires_at")
    private OffsetDateTime claimExpiresAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}