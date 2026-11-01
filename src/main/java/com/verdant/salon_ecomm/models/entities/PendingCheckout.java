package com.verdant.salon_ecomm.models.entities;

import com.verdant.salon_ecomm.models.enums.DeliveryOption;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// A checkout waiting for its payment (V20). Becomes an Order only once Stripe confirms the charge;
// until then it holds no stock and is invisible to order history. Prices are frozen here, so the
// order is created at exactly what the customer was charged.
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "pending_checkouts")
public class PendingCheckout {

    // productId + quantity + delivery option are what the customer bought; unitPrice is frozen at draft time.
    public record Line(UUID productId, int quantity, DeliveryOption deliveryOption, BigDecimal unitPrice) {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Set when staff are paying on a customer's behalf; null for the customer's own checkout.
    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items", nullable = false, columnDefinition = "jsonb")
    private List<Line> items;

    @Embedded
    private Address shippingAddress;

    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod;

    @Column(name = "promo_code", length = 50)
    private String promoCode;

    @Column(name = "promo_discount_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal promoDiscountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "wallet_amount_applied", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal walletAmountApplied = BigDecimal.ZERO;

    @Column(name = "amount_due", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountDue;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
