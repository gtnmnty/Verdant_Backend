package com.verdant.salon_ecomm.dtos.promocode.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// M-10: checked by @Valid in PromoCodeResolver.adminUpsertPromoCode. Before, discountPercent had no
// range, so a negative percent gave a negative discount (raising the order total) and 150% was only
// caught later by a clamp. Limits follow the promo_codes columns (code 30, discount_percent 5,2,
// min_order_amount 10,2). endsAt > startsAt needs both fields, so PromoCodeService.upsert checks it.
public record AdminUpsertPromoCodeInput(
    UUID id,                       // null = create, present = update

    @NotBlank(message = "Code is required")
    @Size(max = 30, message = "Code must be at most 30 characters")
    String code,

    @NotNull(message = "Discount percent is required")
    @DecimalMin(value = "0.01", message = "Discount must be at least 0.01%")
    @DecimalMax(value = "100.00", message = "Discount can't be more than 100%")
    @Digits(integer = 3, fraction = 2, message = "Discount can have at most 2 decimal places")
    BigDecimal discountPercent,

    @PositiveOrZero(message = "Minimum order amount can't be negative")
    @Digits(integer = 8, fraction = 2, message = "Minimum order amount is too large or has more than 2 decimals")
    BigDecimal minOrderAmount,

    @Positive(message = "Max uses per customer must be at least 1")
    Integer maxUsesPerUser,

    OffsetDateTime startsAt,
    OffsetDateTime endsAt,

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    String description
) {}
