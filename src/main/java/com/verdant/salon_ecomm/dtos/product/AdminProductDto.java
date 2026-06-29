package com.verdant.salon_ecomm.dtos.product;

import com.verdant.salon_ecomm.dtos.MediaImageDto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminProductDto(
      String id,
      String name,
      String category,
      String description,
      BigDecimal price,
      BigDecimal salePrice,
      String sku,
      List<MediaImageDto> images,
      List<String> tags,
      List<String> info,
      String badge,
      boolean isFeatured,
      boolean isActive,
      int stockQuantity,
      int lowStockThreshold,
      int reviewCount,
      BigDecimal averageRating,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt
) {}