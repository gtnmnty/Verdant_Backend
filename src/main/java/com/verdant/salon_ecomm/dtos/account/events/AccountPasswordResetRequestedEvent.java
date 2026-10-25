package com.verdant.salon_ecomm.dtos.account.events;

import com.verdant.salon_ecomm.models.entities.User;

import java.util.UUID;

// Published when staff issue a reset link: from createAccount (newAccount = true, the user has
// never had a password) or from sendPasswordReset (newAccount = false).
// AccountPasswordResetEmailListener mails resetToken as a link; it's the only place the raw token goes.
public record AccountPasswordResetRequestedEvent(
    UUID accountId,
    String email,
    String resetToken,
    boolean newAccount,
    User actor
) {
    @Override
    public String toString() {
        return "AccountPasswordResetRequestedEvent[accountId=" + accountId
            + ", email=" + email
            + ", resetToken=[REDACTED]"
            + ", newAccount=" + newAccount
            + ", actor=" + actor
            + "]";
    }
}
