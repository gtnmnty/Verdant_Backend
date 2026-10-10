package com.verdant.salon_ecomm.dtos.support;

import com.verdant.salon_ecomm.models.enums.support.SupportTicketCategory;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketPriority;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


//Used for both customer and staff views. The mapper decides what is exposed:
// customers get no internal notes and no assignedTo.
public record SupportTicketDetailDto(
    UUID id,
    String ticketNumber,
    String subject,
    SupportTicketCategory category,
    SupportTicketStatus status,
    SupportTicketPriority priority,
    String contactName,
    String contactEmail,
    SupportTicketUserDto customer,
    SupportTicketUserDto assignedTo,
    List<SupportTicketMessageDto> messages,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime resolvedAt
) {}
