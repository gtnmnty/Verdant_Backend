package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.models.enums.ItemCatalog;

import java.math.BigDecimal;
import java.util.UUID;

// Component names must match the OrderItemProduct schema type (catalog, not itemCatalog).
public record OrderItemProductDto(
    UUID id,
    String name,
    ItemCatalog catalog,
    BigDecimal price
) {}
