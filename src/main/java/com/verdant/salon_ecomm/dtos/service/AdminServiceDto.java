package com.verdant.salon_ecomm.dtos.service;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminServiceDto(
    String id,
    String name,
    String category,
    String description,
    BigDecimal price,
    List<MediaImageDto> images,
    List<String> tags,
    List<String> info,
    String badge,
    CollectionStatus status,
    int reviewCount,
    BigDecimal averageRating,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
