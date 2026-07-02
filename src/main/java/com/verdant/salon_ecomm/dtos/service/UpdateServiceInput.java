package com.verdant.salon_ecomm.dtos.service;

import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record UpdateServiceInput(
    String name,
    String subname,
    ItemCatalog catalog,
    int durationInMinutes,
    BigDecimal price,
    String badge,
    String description,
    List<String> images,
    List<String> tags,
    List<String> info,
    CollectionStatus status,
    boolean isHomeService,
    boolean isFeatured,
    Set<Long> stylistsId
) {}
