package com.verdant.salon_ecomm.dtos.support.admin;

import java.util.UUID;

public record AdminAddSupportTicketMessageInput(UUID ticketId, String body, Boolean internal) {}
