package com.verdant.salon_ecomm.dtos.account.events;

import com.verdant.salon_ecomm.models.entities.User;

import java.util.UUID;

public record AccountEmailChangedEvent(
    UUID accountId,
    String oldEmail,
    String newEmail,
    String verificationCode,
    User actor
) {
    // The default record toString() would print the one-time code, so any listener or
    // debug log that prints the whole event would leak it. The code is masked here.
    @Override
    public String toString() {
        return "AccountEmailChangedEvent[accountId=" + accountId
            + ", oldEmail=" + oldEmail
            + ", newEmail=" + newEmail
            + ", verificationCode=******]";
    }
}
