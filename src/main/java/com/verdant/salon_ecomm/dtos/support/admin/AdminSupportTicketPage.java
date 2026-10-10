package com.verdant.salon_ecomm.dtos.support.admin;

import java.util.List;

public record AdminSupportTicketPage(
    List<AdminSupportTicketSummaryDto> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
