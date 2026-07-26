package com.verdant.salon_ecomm.dtos.payment;
import java.math.BigDecimal;

public record PaymentIntentDto(
    String stripePaymentIntentId,
    String clientSecret,
    String status,
    BigDecimal amount,
    String currency
) {}

