package com.verdant.salon_ecomm.dtos.service;

import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@AllArgsConstructor
@Data
public class UpdateServiceRequest {
    @NotBlank
    String name;

    String description;
    String category;
    String brand;

    @NotBlank
    String sku;
    String badge;
    List<String> tags;
    List<String> imageUrls;
    Boolean isFeatured;

    @Enumerated(EnumType.STRING)
    CollectionStatus status;

    @NotNull
    @Positive
    BigDecimal price;

    @PositiveOrZero
    BigDecimal salePrice;

    @NotNull
    @PositiveOrZero
    Integer stock;

    @NotNull
    @PositiveOrZero
    Integer lowStockThreshold;
}
