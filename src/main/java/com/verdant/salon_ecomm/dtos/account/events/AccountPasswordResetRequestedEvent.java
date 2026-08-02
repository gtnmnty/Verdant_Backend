package com.verdant.salon_ecomm.dtos.account.events;

import java.util.UUID;

public record AccountPasswordResetRequestedEvent(UUID accountId, String email, String resetToken) {
}
