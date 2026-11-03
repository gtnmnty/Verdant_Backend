package com.verdant.salon_ecomm.dtos.order.events;

import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;
import org.hibernate.Hibernate;

public record OrderCreatedByAdminEvent(Order order, User actor) {
    // Listeners run @Async after the response, outside the request's database session, so a lazy customer can't
    // be loaded then. Load it here, while the service's transaction is still open.
    public OrderCreatedByAdminEvent {
        if (order != null) Hibernate.initialize(order.getUser());
    }
}
