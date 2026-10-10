package com.verdant.salon_ecomm.dtos.order.admin;

import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import jakarta.validation.Valid;

import java.util.List;

public record AdminUpdateOrderInput(
    @Valid AddressInput shippingAddress,
    String paymentMethod,
    OrderStatus orderStatus,
    PaymentStatus paymentStatus,
    List<AdminOrderItemInput> items
) {}
