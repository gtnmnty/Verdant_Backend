package com.verdant.salon_ecomm.dtos.journal;

import java.math.BigDecimal;
import java.util.UUID;

// Slim view of a real catalog Product. id is the real product id: the page links to
// /collections/{id} and "Shop routine" sends it to addToCart.
public record JournalProductDto(
    UUID id,
    String name,
    BigDecimal price,
    BigDecimal salePrice,   // null when not on sale; UI shows salePrice if present
    String imageUrl,        // product's primary image, null if none
    boolean inStock
) {}
