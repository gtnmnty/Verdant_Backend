package com.verdant.salon_ecomm.dtos.payment;

import java.util.UUID;

public record CreatePaymentInput(
    UUID orderId
) {}
