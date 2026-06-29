package com.verdant.salon_ecomm.dtos.service;

import java.math.BigDecimal;
import java.util.List;

public record CreateServiceInput(
    String name,
    String category,
    String description,
    BigDecimal price,
    BigDecimal salePrice,
    String sku,
    String badge,
    List<String> tags,
    List<String> info,
    int stockQuantity,
    int lowStockThreshold,
    boolean isFeatured
){}
