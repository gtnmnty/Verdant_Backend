package com.verdant.salon_ecomm.dtos.product;

import com.verdant.salon_ecomm.models.enums.ItemCatalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ProductResponse {

    public record Summary(
        UUID id,
        String name,
        BigDecimal price,
        ItemCatalog category,
        String primaryImageUrl
    ) {}

    public record Detail(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        BigDecimal salePrice,
        ItemCatalog category,
        String sku,
        List<String> tags,
        BigDecimal averageRating,
        int reviewCount,
        List<String> imageUrls
    ) {}

    public record Admin(
        UUID id,
        String name,
        BigDecimal price,
        int stock,
        ItemCatalog category,
        boolean active,
        int totalSold,
        Instant createdAt,
        Instant updatedAt
    ) {}
}