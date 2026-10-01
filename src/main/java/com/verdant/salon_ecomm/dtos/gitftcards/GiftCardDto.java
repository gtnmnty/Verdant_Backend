package com.verdant.salon_ecomm.dtos.gitftcards;

import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.giftcards.GiftCardStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record GiftCardDto(
    UUID id,
    String code,
    BigDecimal balance,
    BigDecimal initialAmount,
    GiftCardStatus status,
    PaymentStatus paymentStatus,
    String recipientName,
    String recipientEmail,
    String note,
    OffsetDateTime expiresAt,
    OffsetDateTime createdAt
) {}
