package com.verdant.salon_ecomm.dtos.user.events;

import java.util.UUID;

// Published once the user confirmed the code and the email was actually swapped.
public record UserEmailChangedEvent(
    UUID userId,
    String oldEmail,
    String newEmail
) {}
