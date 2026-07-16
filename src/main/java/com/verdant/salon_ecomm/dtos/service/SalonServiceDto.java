package com.verdant.salon_ecomm.dtos.service;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;

import java.math.BigDecimal;
import java.util.UUID;

public record SalonServiceDto(
    UUID id,
    String name,
    String subName,
    ItemCatalog catalog,
    BigDecimal price,
    Integer durationInMinutes,
    String description,
    String badge,
    MediaImageDto primaryImage
) {}
