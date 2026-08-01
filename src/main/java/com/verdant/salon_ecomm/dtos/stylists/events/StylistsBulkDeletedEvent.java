package com.verdant.salon_ecomm.dtos.stylists.events;

import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.entities.User;

import java.util.List;

public record StylistsBulkDeletedEvent(List<Stylist> stylists, User actor) {
}
