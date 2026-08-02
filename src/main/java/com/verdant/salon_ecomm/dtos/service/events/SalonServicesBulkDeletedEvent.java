package com.verdant.salon_ecomm.dtos.service.events;

import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.entities.User;

import java.util.List;

public record SalonServicesBulkDeletedEvent(List<SalonService> services, User actor) {
}
