package com.verdant.salon_ecomm.dtos.gitftcards;

import java.math.BigDecimal;

public record PurchaseGiftCardInput(
    BigDecimal amount,
    String recipientName,
    String recipientEmail,
    String note
) {}