package com.verdant.salon_ecomm.dtos.support;

import java.util.UUID;

// Result row of SupportTicketMessageRepository.countByTicketIds (JPQL constructor expression).
public record TicketMessageCount(UUID ticketId, Long count) {}
