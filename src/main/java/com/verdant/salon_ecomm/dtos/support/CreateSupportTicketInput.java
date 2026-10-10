package com.verdant.salon_ecomm.dtos.support;

import com.verdant.salon_ecomm.models.enums.support.SupportTicketCategory;

public record CreateSupportTicketInput(
    String subject,
    String message,
    SupportTicketCategory category,   // optional, defaults to OTHER
    String contactName,
    String contactEmail
) {}
