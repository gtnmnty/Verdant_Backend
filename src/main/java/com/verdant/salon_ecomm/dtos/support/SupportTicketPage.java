package com.verdant.salon_ecomm.dtos.support;

import java.util.List;

// M-42: one page of the customer's own tickets, same shape as AdminSupportTicketPage. page is 1-based.
public record SupportTicketPage(
    List<SupportTicketSummaryDto> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
