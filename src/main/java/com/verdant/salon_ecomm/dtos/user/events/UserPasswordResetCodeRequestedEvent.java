package com.verdant.salon_ecomm.dtos.user.events;

import java.util.UUID;

// Published by the public forgot-password form once a reset code is stored.
// Carries the plain code only so the after-commit listener can mail it; the DB keeps a hash.
public record UserPasswordResetCodeRequestedEvent(
    UUID userId,
    String email,
    String resetCode
) {
    // Masks the code so logging the whole event can't leak it.
    @Override
    public String toString() {
        return "UserPasswordResetCodeRequestedEvent[userId=" + userId
            + ", email=" + email
            + ", resetCode=******]";
    }
}
