package com.verdant.salon_ecomm.dtos.gitftcards;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AdminIssueGiftCardInput(
    BigDecimal amount,
    String recipientName,
    String recipientEmail,
    String note,
    OffsetDateTime expiresAt
) {}