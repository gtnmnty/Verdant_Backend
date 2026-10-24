package com.verdant.salon_ecomm.models.entities;

import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
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

    @Column(name="status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;

    @Column(length = 255)
    private String orderCode;

    @Column(name = "payment_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Embedded
    private Address shippingAddress;

    // Name/phone entered by staff for an order placed on a customer's behalf.
    // Kept on the order so the customer's saved profile isn't overwritten; null = use the account's details.
    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    // @Builder.Default: the builder ignores plain field initializers, so this was null for every
    // order built via OrderMapper and NPE'd in PaymentService.createStripePaymentIntent.
    @Builder.Default
    @Column(name = "wallet_amount_applied", precision = 10, scale = 2)
    private BigDecimal walletAmountApplied = BigDecimal.ZERO;

    @Column(name = "promo_discount_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal promoDiscountAmount = BigDecimal.ZERO;

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
