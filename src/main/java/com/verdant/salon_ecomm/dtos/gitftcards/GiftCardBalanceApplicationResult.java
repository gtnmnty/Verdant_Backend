package com.verdant.salon_ecomm.dtos.gitftcards;

import java.math.BigDecimal;
import java.util.List;

public record GiftCardBalanceApplicationResult(
    BigDecimal totalApplied,
    List<GiftCardTransactionDto> transactions
) {}
