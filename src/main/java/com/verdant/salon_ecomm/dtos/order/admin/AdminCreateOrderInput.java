package com.verdant.salon_ecomm.dtos.order.admin;

import com.verdant.salon_ecomm.dtos.AddressInput;

import java.util.List;
import java.util.UUID;

public record AdminCreateOrderInput(
    UUID userId,
    String fullName,
    String phone,
    String email,
    AddressInput shippingAddress,
    String paymentMethod,
    List<AdminOrderItemInput> items
) {}
