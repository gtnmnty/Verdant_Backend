package com.verdant.salon_ecomm.dtos.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ProductResponse {

    public record Summary(
            UUID id,
            String name,
            BigDecimal price,
            String category,
            String primaryImageUrl
    ) {}

    public record Detail(
            UUID id,
            String name,
            String description,
            BigDecimal price,
            BigDecimal salePrice,
            String category,
            String brand,
            String sku,
            String tags,
            double averageRating,
            int reviewCount,
            List<String> imageUrls
    ) {}

    public record Admin(
            UUID id,
            String name,
            BigDecimal price,
            int stock,
            String category,
            String brand,
            boolean active,
            int totalSold,
            Instant createdAt,
            Instant updatedAt
    ) {}
}

