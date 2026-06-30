package com.verdant.salon_ecomm.dtos.product;

import java.math.BigDecimal;
import java.util.List;

public record CreateProductInput(
    String name,
    String category,
    String description,
    BigDecimal price,
    BigDecimal salePrice,
    String sku,
    String badge,
    List<String> images,
    List<String> tags,
    List<String> info,
    int stockQuantity,
    int lowStockThreshold,
    boolean isActive,
    Boolean isFeatured
){}
