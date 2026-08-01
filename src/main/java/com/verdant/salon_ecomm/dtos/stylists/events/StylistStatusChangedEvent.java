package com.verdant.salon_ecomm.dtos.stylists.events;

import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.stylists.StylistAccountStatus;

public record StylistStatusChangedEvent(
    Stylist stylist,
    User actor,
    StylistAccountStatus previousStatus
) {
}
