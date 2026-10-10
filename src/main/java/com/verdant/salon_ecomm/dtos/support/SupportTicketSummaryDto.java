package com.verdant.salon_ecomm.dtos.support;

import com.verdant.salon_ecomm.models.enums.support.SupportTicketCategory;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketPriority;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

// Customer-facing list row (Account -> Support -> Ticket History).
public record SupportTicketSummaryDto(
    UUID id,
    String ticketNumber,
    String subject,
    SupportTicketCategory category,
    SupportTicketStatus status,
    SupportTicketPriority priority,
    int messageCount,          // customer-visible messages only
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
