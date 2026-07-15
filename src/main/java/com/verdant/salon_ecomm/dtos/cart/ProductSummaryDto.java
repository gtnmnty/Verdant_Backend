package com.verdant.salon_ecomm.dtos.cart;

import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record ProductSummaryDto(
    UUID id,
    String name,
    ItemCatalog itemCatalog,
    BigDecimal price,
    String image,
    BigDecimal salePrice,
    boolean isFavorite
) {}
