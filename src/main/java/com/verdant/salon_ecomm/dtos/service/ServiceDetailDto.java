package com.verdant.salon_ecomm.dtos.service;

import java.math.BigDecimal;
import java.util.List;

public record ServiceDetailDto(
    String id,
    String name,
    String subName,
    String category,
    String description,
    BigDecimal price,
    int durationMinutes,
    List<String> images,
    List<String> tags,
    List<String> info,
    String badge,
    boolean isFeatured,
    boolean isHomeService,
    int reviewCount,
    BigDecimal averageRating,
    boolean isFavorited
) {}

