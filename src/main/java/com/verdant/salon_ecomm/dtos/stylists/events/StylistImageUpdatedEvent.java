package com.verdant.salon_ecomm.dtos.stylists.events;

import com.verdant.salon_ecomm.models.entities.Stylist;
import com.verdant.salon_ecomm.models.entities.User;

public record StylistImageUpdatedEvent(Stylist stylist, User actor) {
}
