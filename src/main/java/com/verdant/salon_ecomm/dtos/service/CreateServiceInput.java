package com.verdant.salon_ecomm.dtos.service;

import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CreateServiceInput(
    @NotBlank(message = "Service name is required")
    @Valid
    String name,

    @NotBlank(message = "Sub name is required")
    String subName,

    @NotBlank(message = "Category is required")
    String category,
    @NotNull(message = "Price is required")
    @PositiveOrZero(message = "Price can never be lower than 0")
    BigDecimal price,

    @NotNull(message = "Duration is required")
    @Positive(message = "Duration must be greater than 0")
    Integer durationInMinutes,

    @NotNull(message = "Status is required")
    CollectionStatus status,

    String description,
    String badge,
    List<String> tags,
    List<String> info,

    @NotEmpty(message = "Upload at least one image for the service")
    List<String> images,

    boolean isHomeService,
    boolean isFeatured,

    @NotEmpty(message = "Assign at least one stylist")
    Set<UUID> stylistIds
) {}