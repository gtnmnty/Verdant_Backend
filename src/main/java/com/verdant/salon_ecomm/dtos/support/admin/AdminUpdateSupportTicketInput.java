package com.verdant.salon_ecomm.dtos.support.admin;

import com.verdant.salon_ecomm.models.enums.support.SupportTicketPriority;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketStatus;

//Both fields optional - null means "leave unchanged".
public record AdminUpdateSupportTicketInput(SupportTicketStatus status, SupportTicketPriority priority) {}
