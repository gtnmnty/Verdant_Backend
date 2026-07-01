package com.verdant.salon_ecomm.dtos.reviews;

import java.util.List;

public record ReviewConnection(
        List<ReviewDto> items,
        long totalCount,
        int totalPages,
        int currentPage
) {}