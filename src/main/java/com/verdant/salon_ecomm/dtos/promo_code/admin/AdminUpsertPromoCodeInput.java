package com.verdant.salon_ecomm.dtos.promo_code.admin;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUpsertPromoCodeInput(
    UUID id,                       // null = create, present = update
    String code,
    BigDecimal discountPercent,
    BigDecimal minOrderAmount,
    Integer maxUsesPerUser,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    String description
) {}