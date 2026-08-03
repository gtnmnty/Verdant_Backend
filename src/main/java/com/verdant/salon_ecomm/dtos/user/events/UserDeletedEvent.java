package com.verdant.salon_ecomm.dtos.user.events;

import com.verdant.salon_ecomm.entities.User;

public record UserDeletedEvent(User user) {
}
