package com.verdant.salon_ecomm.dtos.cart;

import com.verdant.salon_ecomm.models.enums.ItemCatalog;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemSummaryDto(
    UUID id,
    String name,
    ItemCatalog itemCatalog,
    BigDecimal price,
    String image,
    BigDecimal salePrice,
    boolean isFavorite
) {}
