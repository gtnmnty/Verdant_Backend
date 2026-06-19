package com.verdant.salon_ecomm.dtos.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class SalonServiceResponse {

    public record Summary(
            UUID id,
            String name,
            String subName,
            BigDecimal price,
            int durationMinutes,
            String category,
            String primaryImageUrl,
            double averageRating,
            int reviewCount
    ) {}

    public record Detail(
            UUID id,
            String name,
            String subName,
            String description,
            BigDecimal price,
            int durationMinutes,
            String category,
            List<String> imageUrls,
            double averageRating,
            int reviewCount
    ) {}

    public record Admin(
            UUID id,
            String name,
            BigDecimal price,
            int durationMinutes,
            String category,
            boolean featured,
            boolean active,
            int totalBookings,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
