package com.verdant.salon_ecomm.dtos.service;

import com.verdant.salon_ecomm.models.enums.CollectionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UpdateServiceInput(
    UUID id,
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
    List<String> images,
    Boolean isHomeService,
    Boolean isFeatured,
    Set<UUID> stylistIds
) {}