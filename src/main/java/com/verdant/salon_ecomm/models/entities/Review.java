package com.verdant.salon_ecomm.models.entities;

import com.verdant.salon_ecomm.models.enums.ItemType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
// M-32: one review per user per target, enforced by the database (index created in V10). ReviewService
// also locks the target row, so this constraint is only hit by something that bypasses the service.
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(
    name = "uq_reviews_user_target", columnNames = {"user_id", "target_type", "target_id"}))
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "target_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ItemType targetType;

    @Column(nullable = false)
    private Short stars;

    @Column(columnDefinition = "text")
    private String text;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

}
