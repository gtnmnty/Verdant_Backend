package com.verdant.salon_ecomm.dtos.user.events;

import java.util.UUID;

// Published by sign-up and "resend code" once a verification code is stored.
// Carries the plain code only so the after-commit listener can mail it; the DB keeps a hash.
public record UserVerificationCodeRequestedEvent(
    UUID userId,
    String email,
    String verificationCode
) {
    // Masks the code so logging the whole event can't leak it.
    @Override
    public String toString() {
        return "UserVerificationCodeRequestedEvent[userId=" + userId
            + ", email=" + email
            + ", verificationCode=******]";
    }
}
