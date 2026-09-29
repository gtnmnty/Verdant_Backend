package com.verdant.salon_ecomm.dtos.stylists.events;

import com.verdant.salon_ecomm.models.entities.Stylist;
import com.verdant.salon_ecomm.models.entities.User;

import java.util.List;

public record StylistsBulkDeletedEvent(List<Stylist> stylists, User actor) {
}
