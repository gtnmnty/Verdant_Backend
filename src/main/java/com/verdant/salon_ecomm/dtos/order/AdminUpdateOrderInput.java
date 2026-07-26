package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;

import java.util.List;

public record AdminUpdateOrderInput(
    AddressInput shippingAddress,
    String paymentMethod,
    OrderStatus orderStatus,
    PaymentStatus paymentStatus,
    List<AdminOrderItemInput> items
) {}
