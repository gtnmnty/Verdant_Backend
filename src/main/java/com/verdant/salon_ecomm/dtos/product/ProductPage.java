package com.verdant.salon_ecomm.dtos.product;

import java.util.List;

public record ProductPage(
    List<ProductSummaryDto> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
