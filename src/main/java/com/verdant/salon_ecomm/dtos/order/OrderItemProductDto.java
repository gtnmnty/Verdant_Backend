package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.models.enums.ItemCatalog;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemProductDto(
    UUID id,
    String name,
    ItemCatalog itemCatalog,
    BigDecimal price
) {}
