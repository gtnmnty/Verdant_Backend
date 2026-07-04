package com.verdant.salon_ecomm.dtos.service;

import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UpdateServiceInput(
    UUID id,
    String name,
    String subName,
    String category,

    @PositiveOrZero(message = "Price can never be lower than 0")
    BigDecimal price,
    @Positive(message = "Duration must be greater than 0")
    Integer durationInMinutes,

    CollectionStatus status,
    String description,
    String badge,
    List<String> tags,
    List<String> info,
    List<String> images,
    Boolean isHomeService,
    Boolean isFeatured,
    Set<UUID> stylistIds
) {}