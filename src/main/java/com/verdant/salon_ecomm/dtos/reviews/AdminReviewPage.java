package com.verdant.salon_ecomm.dtos.reviews;

import java.util.List;

public record AdminReviewPage(
    List<AdminReviewDto> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
