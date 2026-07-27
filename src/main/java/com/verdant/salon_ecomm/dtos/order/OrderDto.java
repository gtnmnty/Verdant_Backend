package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderDto(
    UUID id,
    String orderCode,
    OrderStatus orderStatus,
    PaymentStatus paymentStatus,
    String paymentMethod,
    BigDecimal subtotal,
    BigDecimal deliveryFee,
    BigDecimal total,
    List<OrderItemDto> items,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
