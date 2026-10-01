package com.verdant.salon_ecomm.dtos.gitftcards;

import com.verdant.salon_ecomm.models.enums.giftcards.GiftCardTransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record GiftCardTransactionDto(
    UUID id,
    GiftCardTransactionType type,
    BigDecimal amount,
    String description,
    UUID orderId,
    OffsetDateTime createdAt
) {}
