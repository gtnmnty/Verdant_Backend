package com.verdant.salon_ecomm.dtos;

public record MediaImageDto(
    String id,
    String url,
    String publicId,
    boolean isPrimary,
    int sortOrder
) {}
