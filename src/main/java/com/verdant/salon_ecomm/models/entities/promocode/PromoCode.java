package com.verdant.salon_ecomm.models.entities.promocode;

import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.PromoCodeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "promo_codes")
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // M-15: optimistic lock (column added in V9). Two admins editing the same code (or one editing while
    // another changes its status) get a 409 on the second save instead of losing the first edit.
    @Version
    private Long version;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent; // e.g. 10.00 = 10%

    @Column(name = "min_order_amount", precision = 10, scale = 2)
    private BigDecimal minOrderAmount; // nullable = no minimum

    @Column(name = "max_uses_per_user", nullable = false)
    @Builder.Default
    private Integer maxUsesPerUser = 1;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PromoCodeStatus status = PromoCodeStatus.ACTIVE;

    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
