package com.verdant.salon_ecomm.dtos.journal.admin;

import java.util.List;

public record AdminJournalStoryPage(
    List<AdminJournalStoryDto> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
