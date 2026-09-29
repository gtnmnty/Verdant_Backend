package com.verdant.salon_ecomm.dtos.order.events;

import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;

import java.util.List;

public record OrdersDeletedEvent(
    List<Order> orders,
    User actor
) {}
