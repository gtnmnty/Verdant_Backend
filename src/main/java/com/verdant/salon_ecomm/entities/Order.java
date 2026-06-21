package com.verdant.salon_ecomm.entities;

import com.verdant.salon_ecomm.models.enums.OrderStatus;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
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
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus = OrderStatus.PLACED;

    @Column(name = "payment_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus =  PaymentStatus.PROCESSED;

    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "snap_address_line1", length = 255)
    private String snapAddressLine1;

    @Column(name = "snap_address_line2", length = 255)
    private String snapAddressLine2;

    @Column(name = "snap_address_city", length = 100)
    private String snapAddressCity;

    @Column(name = "snap_address_state", length = 100)
    private String snapAddressState;

    @Column(name = "snap_address_postal", length = 20)
    private String snapAddressPostal;

    @Column(name = "snap_address_country", length = 2)
    private String snapAddressCountry;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(columnDefinition = "text")
    private String notes;

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
