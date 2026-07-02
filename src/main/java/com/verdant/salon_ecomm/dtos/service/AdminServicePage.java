package com.verdant.salon_ecomm.dtos.service;

import java.util.List;

public record AdminServicePage(
    List<AdminServiceDto> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
