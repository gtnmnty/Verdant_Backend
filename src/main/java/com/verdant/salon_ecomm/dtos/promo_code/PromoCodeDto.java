package com.verdant.salon_ecomm.dtos.promo_code;

import com.verdant.salon_ecomm.models.enums.promo.PromoCodeStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PromoCodeDto(
    UUID id,
    String code,
    BigDecimal discountPercent,
    BigDecimal minOrderAmount,
    Integer maxUsesPerUser,
    PromoCodeStatus status,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    String description
) {}