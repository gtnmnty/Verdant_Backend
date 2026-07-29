package com.verdant.salon_ecomm.dtos.order.events;

import com.verdant.salon_ecomm.entities.Order;
import com.verdant.salon_ecomm.entities.User;

public record OrderCreatedByAdminEvent(Order order, User actor) {
}
