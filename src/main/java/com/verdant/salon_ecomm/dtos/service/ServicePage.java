package com.verdant.salon_ecomm.dtos.service;

import com.verdant.salon_ecomm.dtos.product.AdminProductDto;

import java.util.List;

public record ServicePage(
    List<AdminProductDto> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
