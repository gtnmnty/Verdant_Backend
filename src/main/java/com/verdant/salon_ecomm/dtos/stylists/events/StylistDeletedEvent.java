package com.verdant.salon_ecomm.dtos.stylists.events;

import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.entities.User;

public record StylistDeletedEvent(Stylist stylist, User actor) {
}
