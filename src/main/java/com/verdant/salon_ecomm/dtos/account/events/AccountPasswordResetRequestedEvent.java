package com.verdant.salon_ecomm.dtos.account.events;

import com.verdant.salon_ecomm.entities.User;

import java.util.UUID;

public record AccountPasswordResetRequestedEvent(
    UUID accountId,
    String email,
    String resetToken,
    User actor
) {}
