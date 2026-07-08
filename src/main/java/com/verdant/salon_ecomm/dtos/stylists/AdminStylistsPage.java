package com.verdant.salon_ecomm.dtos.stylists;

import java.util.List;

public record AdminStylistsPage(
    List<AdminStylistsDto> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
