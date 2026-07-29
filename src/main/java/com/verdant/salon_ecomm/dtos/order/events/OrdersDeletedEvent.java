package com.verdant.salon_ecomm.dtos.order.events;

import com.verdant.salon_ecomm.entities.Order;
import com.verdant.salon_ecomm.entities.User;

import java.util.List;

public record OrdersDeletedEvent(List<Order> orders, User actor) {
}
