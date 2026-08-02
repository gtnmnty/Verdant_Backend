package com.verdant.salon_ecomm.dtos.account.events;

import com.verdant.salon_ecomm.entities.User;

public record AccountCreatedEvent(User account, User actor) {
}
