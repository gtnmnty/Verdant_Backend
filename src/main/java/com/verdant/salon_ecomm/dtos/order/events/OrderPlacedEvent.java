package com.verdant.salon_ecomm.dtos.order.events;

import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;

public record OrderPlacedEvent(Order order, User customer) {
}
