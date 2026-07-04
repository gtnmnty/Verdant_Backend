package com.verdant.salon_ecomm.dtos.service;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.dtos.stylists.StylistSummaryDto;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminServiceDto(
    String id,
    String name,
    String subName,
    String category,
    BigDecimal price,
    Integer durationInMinutes,
    CollectionStatus status,
    String description,
    String badge,
    List<String> tags,
    List<String> info,
    int reviewCount,
    BigDecimal averageRating,
    boolean isHomeService,
    boolean isFeatured,
    List<MediaImageDto> images,
    List<StylistSummaryDto> stylistIds,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}