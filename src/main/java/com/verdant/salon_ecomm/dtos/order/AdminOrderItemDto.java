package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.models.enums.DeliveryOption;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminOrderItemDto(
    UUID id,
    OrderItemProductDto product,
    String productName,
    String productImage,
    Integer quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal,
    DeliveryOption deliveryOption,
    String quantityLabel
) {}