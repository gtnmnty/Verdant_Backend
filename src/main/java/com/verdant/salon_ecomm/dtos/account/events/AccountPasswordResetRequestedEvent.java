package com.verdant.salon_ecomm.dtos.account.events;

import com.verdant.salon_ecomm.models.entities.User;

import java.util.UUID;

public record AccountPasswordResetRequestedEvent(
    UUID accountId,
    String email,
    String resetToken,
    User actor
) {
    @Override
    public String toString() {
        return "AccountPasswordResetRequestedEvent[accountId=" + accountId
            + ", email=" + email
            + ", resetToken=[REDACTED]"
            + ", actor=" + actor
            + "]";
    }
}
