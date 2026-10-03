package com.verdant.salon_ecomm.models.entities.giftcards;

import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.giftcards.GiftCardStatus;
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
@Table(name = "gift_cards")
public class GiftCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchaser_id")
    private User purchaser;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    // @Builder.Default is required: without it Lombok's builder ignores the initializer
    // and the field is null whenever the builder doesn't set it explicitly.
    @Builder.Default
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "initial_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal initialAmount;

    @Builder.Default
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private GiftCardStatus status = GiftCardStatus.ACTIVE;

    // Safe default: a card is unpaid until something explicitly marks it PAID.
    @Builder.Default
    @Column(name = "payment_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // The code is a bearer secret -
    // use this anywhere that isn't the owner's own screen (audit, staff notifications, logs).
    public String maskedCode() {
        if (code == null || code.length() < 9) return "****";
        return code.substring(0, 4) + "****" + code.substring(8); // VLX-****-AB12
    }

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