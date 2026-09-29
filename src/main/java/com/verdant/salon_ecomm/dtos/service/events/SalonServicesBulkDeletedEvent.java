package com.verdant.salon_ecomm.dtos.service.events;

import com.verdant.salon_ecomm.models.entities.SalonService;
import com.verdant.salon_ecomm.models.entities.User;

import java.util.List;

public record SalonServicesBulkDeletedEvent(List<SalonService> services, User actor) {
}
