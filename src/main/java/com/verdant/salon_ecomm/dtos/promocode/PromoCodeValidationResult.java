package com.verdant.salon_ecomm.dtos.promocode;

import java.math.BigDecimal;

public record PromoCodeValidationResult(
    BigDecimal discountAmount,
    PromoCodeDto promoCode
) {}