package com.verdant.salon_ecomm.dtos.journal;

import java.util.List;

public record JournalStoryPage(
    List<JournalStoryDto> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
