package com.verdant.salon_ecomm.dtos.product;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailDto(
      String id,
      String name,
      String category,
      String description,
      BigDecimal price,
      BigDecimal salePrice,
      String sku,
      List<String> images,
      List<String> tags,
      List<String> info,
      String badge,
      boolean inStock,
      int reviewCount,
      BigDecimal averageRating,
      boolean isFavorited
) {}
