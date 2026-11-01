package com.verdant.salon_ecomm.dtos.checkout;

import java.math.BigDecimal;
import java.util.UUID;

// What the browser needs to show the Stripe card form for a draft checkout. No order exists yet.
public record CheckoutSessionDto(
    UUID draftId,
    String paymentIntentId,
    String clientSecret,
    BigDecimal amount,
    String currency
) {}
