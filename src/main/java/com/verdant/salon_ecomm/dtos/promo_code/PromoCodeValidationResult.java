package com.verdant.salon_ecomm.dtos.promo_code;

import java.math.BigDecimal;

public record PromoCodeValidationResult(
    BigDecimal discountAmount,
    PromoCodeDto promoCode
) {}