package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.models.enums.OrderStatus;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderSummaryDto(
        UUID id,
        String reference,
        String customerName,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        Integer itemCount,
        BigDecimal total,
        OffsetDateTime placedAt,
        List<OrderItemDto> items
) {
}
