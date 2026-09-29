package com.verdant.salon_ecomm.dtos.service.events;

import com.verdant.salon_ecomm.models.entities.SalonService;
import com.verdant.salon_ecomm.models.entities.User;

public record SalonServiceCreatedEvent(SalonService service, User actor) {
}
