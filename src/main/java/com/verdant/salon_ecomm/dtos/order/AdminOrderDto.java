package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.entities.Address;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminOrderDto(
    UUID id,
    String orderCode,
    User user,
    OrderStatus orderStatus,
    PaymentStatus paymentStatus,
    String paymentMethod,
    Address address,
    BigDecimal subtotal,
    BigDecimal deliveryFee,
    BigDecimal total,
    int itemCount,
    List<AdminOrderItemDto> items,
    List<OrderActivityDto> activity,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
