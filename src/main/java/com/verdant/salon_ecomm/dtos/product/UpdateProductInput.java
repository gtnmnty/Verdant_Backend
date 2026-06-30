package com.verdant.salon_ecomm.dtos.product;

import com.verdant.salon_ecomm.models.enums.CollectionStatus;

import java.math.BigDecimal;
import java.util.List;

public record UpdateProductInput(
    String id,
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
    Integer stockQuantity,
    Integer lowStockThreshold,
    Boolean isFeatured,
    CollectionStatus status
){}
