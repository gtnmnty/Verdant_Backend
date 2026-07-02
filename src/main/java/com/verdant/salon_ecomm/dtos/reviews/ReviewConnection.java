package com.verdant.salon_ecomm.dtos.reviews;

import java.util.List;

public record ReviewConnection(
        List<ReviewDto> items,
        int totalCount,
        int totalPages,
        int currentPage
) {}