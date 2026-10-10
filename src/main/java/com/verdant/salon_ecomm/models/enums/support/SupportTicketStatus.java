package com.verdant.salon_ecomm.models.enums.support;

public enum SupportTicketStatus {
    OPEN,       // new, or reopened by a customer reply   (UI: "Open")
    IN_REVIEW,  // staff is working on it                 (UI: "In Review")
    RESOLVED    // answered / fixed                       (UI: "Resolved")
}
