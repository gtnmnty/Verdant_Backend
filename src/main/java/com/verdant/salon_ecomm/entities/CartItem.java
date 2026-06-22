package com.verdant.salon_ecomm.entities;

import com.verdant.salon_ecomm.models.enums.DeliveryOption;
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
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "delivery_option", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private DeliveryOption deliveryOption;

    @Column(name = "added_at")
    private OffsetDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        addedAt = OffsetDateTime.now();
    }

}
