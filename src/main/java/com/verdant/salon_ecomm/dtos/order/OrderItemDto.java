package com.verdant.salon_ecomm.dtos.order;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemDto(
        UUID id,
        UUID productId,
        String productName,
        String productImage,
        String productCategory,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
}
