package com.verdant.salon_ecomm.dtos.support;

import java.util.UUID;

// Customer reply. Deliberately has no "internal" flag - customers can't write staff notes. */
public record AddSupportTicketMessageInput(UUID ticketId, String body) {}
