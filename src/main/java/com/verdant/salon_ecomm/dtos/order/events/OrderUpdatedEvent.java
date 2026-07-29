package com.verdant.salon_ecomm.dtos.order.events;

import com.verdant.salon_ecomm.entities.Order;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;

public record OrderUpdatedEvent(
        Order order,
        User actor,
        OrderStatus previousOrderStatus,
        PaymentStatus previousPaymentStatus
) {
    public boolean orderStatusChanged() {
        return previousOrderStatus != order.getOrderStatus();
    }

    public boolean paymentStatusChanged() {
        return previousPaymentStatus != order.getPaymentStatus();
    }
}
