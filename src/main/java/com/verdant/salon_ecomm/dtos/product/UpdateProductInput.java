package com.verdant.salon_ecomm.dtos.product;

import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

// M-10: checked by @Valid in ProductResolver.updateProduct. Every field is optional (null = keep),
// so only ranges and lengths are checked, never presence. Same limits as CreateProductInput.
public record UpdateProductInput(
    String id,

    @Size(max = 200, message = "Product name must be at most 200 characters")
    String name,

    String category,
    String description,

    @PositiveOrZero(message = "Price can't be negative")
    @Digits(integer = 8, fraction = 2, message = "Price is too large or has more than 2 decimals")
    BigDecimal price,

    @PositiveOrZero(message = "Sale price can't be negative")
    @Digits(integer = 8, fraction = 2, message = "Sale price is too large or has more than 2 decimals")
    BigDecimal salePrice,

    @Size(max = 100, message = "SKU must be at most 100 characters")
    String sku,

    @Size(max = 50, message = "Badge must be at most 50 characters")
    String badge,

    List<String> images,
    List<String> tags,
    List<String> info,

    @PositiveOrZero(message = "Stock quantity can't be negative")
    Integer stockQuantity,

    @PositiveOrZero(message = "Low-stock threshold can't be negative")
    Integer lowStockThreshold,

    Boolean isFeatured,
    CollectionStatus status
){}
