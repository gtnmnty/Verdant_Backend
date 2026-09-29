package com.verdant.salon_ecomm.dtos.stylists.events;

import com.verdant.salon_ecomm.models.entities.Stylist;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.stylists.StylistAccountStatus;

public record StylistStatusChangedEvent(
    Stylist stylist,
    User actor,
    StylistAccountStatus previousStatus
) {
}
