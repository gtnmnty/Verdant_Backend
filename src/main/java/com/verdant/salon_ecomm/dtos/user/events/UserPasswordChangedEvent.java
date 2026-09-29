package com.verdant.salon_ecomm.dtos.user.events;

import com.verdant.salon_ecomm.models.entities.User;

public record UserPasswordChangedEvent(User user) {
}
