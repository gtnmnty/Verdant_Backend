package com.verdant.salon_ecomm.dtos.order.admin;

import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminUpdateOrderInput(
    // Saved on the order (contactName/contactPhone), never on the customer's profile.
    String fullName,
    String phone,
    @Valid AddressInput shippingAddress,
    String paymentMethod,
    OrderStatus orderStatus,
    PaymentStatus paymentStatus,
    // M-16: null = items unchanged; when sent, at least one line (an empty list would delete every item
    // and leave an order with a 0 subtotal). @Valid checks each line's quantity and ids.
    @Valid @Size(min = 1, message = "An order needs at least one item") List<AdminOrderItemInput> items
) {}
