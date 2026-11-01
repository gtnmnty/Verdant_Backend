package com.verdant.salon_ecomm.dtos.order.events;

import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;

import java.math.BigDecimal;

// refundAmount: what goes back to the customer's card (zero when nothing was charged to a card).
public record OrderCancelledByCustomerEvent(
    Order order, User customer, OrderStatus previousOrderStatus, BigDecimal refundAmount
) {}
