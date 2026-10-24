package com.verdant.salon_ecomm.dtos.user.events;

import java.util.UUID;

// Published when a user asks to move their account to a new email.
// Carries the plain code only so the after-commit listener can mail it; the DB keeps a hash.
public record UserEmailChangeRequestedEvent(
    UUID userId,
    String newEmail,
    String verificationCode
) {
    // Masks the code so logging the whole event can't leak it.
    @Override
    public String toString() {
        return "UserEmailChangeRequestedEvent[userId=" + userId
            + ", newEmail=" + newEmail
            + ", verificationCode=******]";
    }
}
