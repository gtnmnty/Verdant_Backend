package com.verdant.salon_ecomm.dtos.promocode;

import com.verdant.salon_ecomm.models.enums.PromoCodeStatus;

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