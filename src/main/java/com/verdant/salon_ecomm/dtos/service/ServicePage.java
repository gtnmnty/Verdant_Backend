package com.verdant.salon_ecomm.dtos.service;

import com.verdant.salon_ecomm.entities.SalonService;

import java.util.List;

public record ServicePage(
    List<SalonService> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
