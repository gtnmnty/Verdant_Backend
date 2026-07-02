package com.verdant.salon_ecomm.dtos.service;

import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record CreateServiceInput(
    @NotBlank(message = "Service name is required")
    String name,
    String subname,

    @NotNull(message = "Category is required")
    ItemCatalog catalog,

    @NotNull(message = "Duration is required")
    @Positive(message = "Duration must be greater than 0")
    int durationInMinutes,

    @PositiveOrZero(message = "price can never be lower than 0")
    BigDecimal price,
    String badge,
    String description,

    @NotEmpty(message = "Upload at least an image for the service")
    List<String> images,
    List<String> tags,
    List<String> info,

    CollectionStatus status,
    boolean isHomeService,
    boolean isFeatured,

    @NotEmpty(message = "Assign at least one stylist")
    Set<Long> stylistsId
) {}
