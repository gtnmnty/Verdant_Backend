package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.models.enums.DeliveryOption;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminOrderItemDto(
    UUID id,
    Product product,
    String productName,
    String productImage,
    Integer quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal,
    DeliveryOption deliveryOption,
    String itemLine
) {}
