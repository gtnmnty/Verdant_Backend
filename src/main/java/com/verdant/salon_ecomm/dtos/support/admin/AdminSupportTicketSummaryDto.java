package com.verdant.salon_ecomm.dtos.support.admin;

import com.verdant.salon_ecomm.dtos.support.SupportTicketUserDto;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketCategory;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketPriority;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminSupportTicketSummaryDto(
    UUID id,
    String ticketNumber,
    String subject,
    SupportTicketCategory category,
    SupportTicketStatus status,
    SupportTicketPriority priority,
    SupportTicketUserDto customer,
    SupportTicketUserDto assignedTo,   // null = unassigned
    int messageCount,                  // includes internal notes
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
