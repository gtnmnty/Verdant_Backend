package com.verdant.salon_ecomm.dtos.support;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SupportTicketMessageDto(
    UUID id,
    SupportTicketUserDto author,
    boolean fromStaff,
    boolean internal,
    String body,
    OffsetDateTime createdAt
) {}
