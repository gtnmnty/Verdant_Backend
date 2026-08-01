package com.verdant.salon_ecomm.dtos.stylists.events;

import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.entities.User;

public record StylistAssignedToServicesEvent(
    Stylist stylist,
    User actor,
    int previousServiceCount,
    int newServiceCount
) {
}
