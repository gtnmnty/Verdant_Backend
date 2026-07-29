package com.verdant.salon_ecomm.dtos.payment;

import com.verdant.salon_ecomm.models.enums.PaymentStatus;

import java.util.UUID;

public record PaymentStatusChangedEvent(
    UUID orderId,
    UUID userId,
    PaymentStatus previousStatus,
    PaymentStatus newStatus,
    String stripeEventType
) {}
