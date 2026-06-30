package com.verdant.salon_ecomm.dtos.product;

import java.math.BigDecimal;

public record ProductSummaryDto(
    String id,
    String name,
    String category,
    BigDecimal price,
    BigDecimal salePrice,
    String primaryImageUrl,
    String badge,
    boolean isFavorited
) {}
