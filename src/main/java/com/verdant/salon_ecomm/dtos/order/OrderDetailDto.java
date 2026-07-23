package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.models.enums.OrderStatus;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderDetailDto(
        UUID id,
        String reference,
        String customerName,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        String paymentMethod,
        Integer itemCount,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal total,
        String notes,
        OffsetDateTime placedAt,
        OffsetDateTime updatedAt,
        List<OrderItemDto> items,
        List<OrderActivityDto> activity,
        AddressDto shippingAddress
) {
    public record AddressDto(
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country
    ) {
    }
}
